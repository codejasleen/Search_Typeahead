package com.typeahead.batch;

import com.typeahead.cache.DistributedCache;
import com.typeahead.model.QueryFrequency;
import com.typeahead.repository.QueryFrequencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class BatchService {
    private static final Logger log = LoggerFactory.getLogger(BatchService.class);

    private final QueryFrequencyRepository repository;
    private final DistributedCache distributedCache;

    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

    @Value("${app.batch.queue-threshold}")
    private int queueThreshold;

    public BatchService(QueryFrequencyRepository repository, DistributedCache distributedCache) {
        this.repository = repository;
        this.distributedCache = distributedCache;
    }

    public void enqueue(String query) {
        if (query == null || query.isBlank()) {
            return;
        }
        queue.add(query.toLowerCase().trim());
        
        // Immediate flush if threshold is exceeded
        if (queue.size() >= queueThreshold) {
            log.info("Queue threshold reached ({}), forcing flush...", queue.size());
            flush();
        }
    }

    public int getQueueSize() {
        return queue.size();
    }

    public Map<String, Integer> drainAndAggregate() {
        Map<String, Integer> aggregated = new HashMap<>();
        String query;
        while ((query = queue.poll()) != null) {
            aggregated.merge(query, 1, Integer::sum);
        }
        return aggregated;
    }

    @Scheduled(fixedRateString = "${app.batch.flush-interval}")
    @Transactional
    public synchronized void flush() {
        Map<String, Integer> batch = drainAndAggregate();
        if (batch.isEmpty()) {
            return;
        }

        log.info("Drained {} queries from write queue. Processing batch...", batch.size());
        Set<String> affectedPrefixes = new HashSet<>();

        for (Map.Entry<String, Integer> entry : batch.entrySet()) {
            String query = entry.getKey();
            int delta = entry.getValue();

            // Upsert in H2 database
            int updatedRows = repository.incrementCount(query, delta);
            if (updatedRows == 0) {
                // New query: insert record
                QueryFrequency qf = new QueryFrequency();
                qf.setQuery(query);
                qf.setCount(delta);
                repository.save(qf);
            }

            // Collect all prefixes of this query that need cache updates
            for (int i = 1; i <= query.length(); i++) {
                affectedPrefixes.add(query.substring(0, i));
            }
        }

        // Refresh cache for all affected prefixes
        List<QueryFrequency> allQueries = repository.findAllByOrderByCountDesc();
        distributedCache.refreshPrefixes(affectedPrefixes, allQueries);

        log.info("Batch flushed: {} database records updated, {} cache prefixes refreshed", batch.size(), affectedPrefixes.size());
    }
}
