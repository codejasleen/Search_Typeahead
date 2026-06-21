package com.typeahead.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typeahead.hashing.ConsistentHashRing;
import com.typeahead.model.QueryFrequency;
import com.typeahead.model.Suggestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DistributedCache {
    private static final Logger log = LoggerFactory.getLogger(DistributedCache.class);
    private static final String CACHE_PREFIX = "cache:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ConsistentHashRing hashRing;
    private final ObjectMapper objectMapper;

    public DistributedCache(RedisTemplate<String, String> redisTemplate,
                            ConsistentHashRing hashRing,
                            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.hashRing = hashRing;
        this.objectMapper = objectMapper;
    }

    public List<Suggestion> getSuggestions(String prefix) {
        String nodeName = hashRing.getNode(prefix);
        Object json = redisTemplate.opsForHash().get(CACHE_PREFIX + nodeName, prefix);
        if (json == null) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue((String) json, new TypeReference<List<Suggestion>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse suggestions from cache for prefix: {}", prefix, e);
            return Collections.emptyList();
        }
    }

    public void putSuggestions(String prefix, List<Suggestion> suggestions) {
        String nodeName = hashRing.getNode(prefix);
        try {
            String json = objectMapper.writeValueAsString(suggestions);
            redisTemplate.opsForHash().put(CACHE_PREFIX + nodeName, prefix, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize suggestions for prefix: {}", prefix, e);
        }
    }

    public String getNodeName(String prefix) {
        return hashRing.getNode(prefix);
    }

    public boolean isHit(String prefix) {
        String nodeName = hashRing.getNode(prefix);
        Boolean hasKey = redisTemplate.opsForHash().hasKey(CACHE_PREFIX + nodeName, prefix);
        return hasKey != null && hasKey;
    }

    public void buildFullCache(List<QueryFrequency> allQueries) {
        log.info("Building full cache for {} queries...", allQueries.size());
        
        // Clear existing cache keys
        redisTemplate.delete(Arrays.asList("cache:node-a", "cache:node-b", "cache:node-c"));
        
        // Build prefix -> top-10 suggestions map
        Map<String, PriorityQueue<Suggestion>> prefixMap = new HashMap<>();
        for (QueryFrequency qf : allQueries) {
            String query = qf.getQuery().toLowerCase().trim();
            long count = qf.getCount();
            for (int i = 1; i <= query.length(); i++) {
                String prefix = query.substring(0, i);
                prefixMap.computeIfAbsent(prefix, k -> new PriorityQueue<>(Comparator.comparingLong(Suggestion::getScore)));
                PriorityQueue<Suggestion> pq = prefixMap.get(prefix);
                pq.offer(new Suggestion(query, count));
                if (pq.size() > 10) {
                    pq.poll();
                }
            }
        }

        log.info("Distributing {} prefixes to Redis sharded hashes...", prefixMap.size());
        
        // Distribute to Redis using pipelines to speed up startup significantly
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Map.Entry<String, PriorityQueue<Suggestion>> entry : prefixMap.entrySet()) {
                String prefix = entry.getKey();
                List<Suggestion> sorted = new ArrayList<>(entry.getValue());
                sorted.sort(Comparator.comparingLong(Suggestion::getScore).reversed());
                
                String nodeName = hashRing.getNode(prefix);
                byte[] key = (CACHE_PREFIX + nodeName).getBytes();
                byte[] field = prefix.getBytes();
                try {
                    byte[] value = objectMapper.writeValueAsBytes(sorted);
                    connection.hashCommands().hSet(key, field, value);
                } catch (JsonProcessingException e) {
                    log.error("Pipeline serialization error for prefix: {}", prefix, e);
                }
            }
            return null;
        });

        log.info("Finished building full cache. Node Distribution: {}", getCacheStats());
    }

    public void refreshPrefixes(Set<String> affectedPrefixes, List<QueryFrequency> allQueries) {
        if (affectedPrefixes.isEmpty()) {
            return;
        }
        
        log.info("Refreshing cache for {} affected prefixes...", affectedPrefixes.size());
        
        // Build prefix -> top-10 suggestions map only for the affected prefixes
        Map<String, PriorityQueue<Suggestion>> prefixMap = new HashMap<>();
        for (QueryFrequency qf : allQueries) {
            String query = qf.getQuery().toLowerCase().trim();
            long count = qf.getCount();
            for (int i = 1; i <= query.length(); i++) {
                String prefix = query.substring(0, i);
                if (affectedPrefixes.contains(prefix)) {
                    prefixMap.computeIfAbsent(prefix, k -> new PriorityQueue<>(Comparator.comparingLong(Suggestion::getScore)));
                    PriorityQueue<Suggestion> pq = prefixMap.get(prefix);
                    pq.offer(new Suggestion(query, count));
                    if (pq.size() > 10) {
                        pq.poll();
                    }
                }
            }
        }

        // Write changes to Redis sharded hashes
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String prefix : affectedPrefixes) {
                PriorityQueue<Suggestion> pq = prefixMap.get(prefix);
                String nodeName = hashRing.getNode(prefix);
                byte[] key = (CACHE_PREFIX + nodeName).getBytes();
                byte[] field = prefix.getBytes();
                
                if (pq != null) {
                    List<Suggestion> sorted = new ArrayList<>(pq);
                    sorted.sort(Comparator.comparingLong(Suggestion::getScore).reversed());
                    try {
                        byte[] value = objectMapper.writeValueAsBytes(sorted);
                        connection.hashCommands().hSet(key, field, value);
                    } catch (JsonProcessingException e) {
                        log.error("Pipeline serialization error during refresh for prefix: {}", prefix, e);
                    }
                } else {
                    // Prefix has no suggestions left
                    connection.hashCommands().hDel(key, field);
                }
            }
            return null;
        });
    }

    public Map<String, Long> getCacheStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("node-a", redisTemplate.opsForHash().size("cache:node-a"));
        stats.put("node-b", redisTemplate.opsForHash().size("cache:node-b"));
        stats.put("node-c", redisTemplate.opsForHash().size("cache:node-c"));
        return stats;
    }
}
