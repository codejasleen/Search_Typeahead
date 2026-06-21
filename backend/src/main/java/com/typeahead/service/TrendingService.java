package com.typeahead.service;

import com.typeahead.model.QueryFrequency;
import com.typeahead.model.Suggestion;
import com.typeahead.repository.QueryFrequencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class TrendingService {
    private static final Logger log = LoggerFactory.getLogger(TrendingService.class);

    private final ConcurrentHashMap<String, AtomicLong> recentCounts = new ConcurrentHashMap<>();
    private final QueryFrequencyRepository repository;

    @Value("${app.trending.weight}")
    private long weight;

    public TrendingService(QueryFrequencyRepository repository) {
        this.repository = repository;
    }

    public void recordRecent(String query) {
        if (query == null || query.isBlank()) {
            return;
        }
        recentCounts.computeIfAbsent(query.toLowerCase().trim(), k -> new AtomicLong()).incrementAndGet();
    }

    public List<Suggestion> getTrending(int limit) {
        List<QueryFrequency> allQueries = repository.findAllByOrderByCountDesc();
        
        Map<String, Long> scores = new HashMap<>();
        
        // Boost existing database queries with recent trend count
        for (QueryFrequency qf : allQueries) {
            String query = qf.getQuery();
            long count = qf.getCount();
            long boost = 0;
            AtomicLong recent = recentCounts.get(query);
            if (recent != null) {
                boost = recent.get() * weight;
            }
            scores.put(query, count + boost);
        }
        
        // Add new queries that are in memory but not yet flushed to database
        for (Map.Entry<String, AtomicLong> entry : recentCounts.entrySet()) {
            scores.putIfAbsent(entry.getKey(), entry.getValue().get() * weight);
        }
        
        // Sort and select top N
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(e -> new Suggestion(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    @Scheduled(fixedRateString = "${app.trending.decay-interval}")
    public void clearRecent() {
        recentCounts.clear();
        log.info("Trending counters cleared (decay interval reached)");
    }
}
