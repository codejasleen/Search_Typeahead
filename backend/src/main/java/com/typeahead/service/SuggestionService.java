package com.typeahead.service;

import com.typeahead.batch.BatchService;
import com.typeahead.cache.DistributedCache;
import com.typeahead.model.QueryFrequency;
import com.typeahead.model.Suggestion;
import com.typeahead.repository.QueryFrequencyRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SuggestionService {

    private final DistributedCache distributedCache;
    private final BatchService batchService;
    private final TrendingService trendingService;
    private final QueryFrequencyRepository repository;

    public SuggestionService(DistributedCache distributedCache,
                             BatchService batchService,
                             TrendingService trendingService,
                             QueryFrequencyRepository repository) {
        this.distributedCache = distributedCache;
        this.batchService = batchService;
        this.trendingService = trendingService;
        this.repository = repository;
    }

    public List<Suggestion> getSuggestions(String prefix) {
        if (prefix == null) {
            return Collections.emptyList();
        }
        prefix = prefix.toLowerCase().trim();
        if (prefix.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. Fetch from sharded cache nodes
        List<Suggestion> results = distributedCache.getSuggestions(prefix);
        
        // 2. Cache miss: fallback to H2 source of truth and update cache
        if (results.isEmpty()) {
            List<QueryFrequency> allQueries = repository.findAllByOrderByCountDesc();
            String finalPrefix = prefix;
            results = allQueries.stream()
                    .filter(qf -> qf.getQuery().toLowerCase().startsWith(finalPrefix))
                    .limit(10)
                    .map(qf -> new Suggestion(qf.getQuery(), qf.getCount()))
                    .collect(Collectors.toList());
            
            if (!results.isEmpty()) {
                distributedCache.putSuggestions(prefix, results);
            }
        }

        return results;
    }

    public void recordSearch(String query) {
        if (query == null) {
            return;
        }
        query = query.toLowerCase().trim();
        if (!query.isEmpty()) {
            batchService.enqueue(query);
            trendingService.recordRecent(query);
        }
    }
}
