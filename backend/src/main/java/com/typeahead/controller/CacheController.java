package com.typeahead.controller;

import com.typeahead.cache.DistributedCache;
import com.typeahead.model.Suggestion;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cache")
public class CacheController {

    private final DistributedCache distributedCache;

    public CacheController(DistributedCache distributedCache) {
        this.distributedCache = distributedCache;
    }

    @GetMapping("/debug")
    public Map<String, Object> debug(@RequestParam String prefix) {
        String cleanPrefix = (prefix != null) ? prefix.toLowerCase().trim() : "";
        String nodeName = distributedCache.getNodeName(cleanPrefix);
        boolean hit = distributedCache.isHit(cleanPrefix);
        List<Suggestion> suggestions = distributedCache.getSuggestions(cleanPrefix);
        
        Map<String, Object> response = new HashMap<>();
        response.put("prefix", cleanPrefix);
        response.put("cacheNode", nodeName != null ? nodeName : "unknown");
        response.put("hit", hit);
        response.put("suggestions", suggestions);
        return response;
    }

    @GetMapping("/stats")
    public Map<String, Long> getStats() {
        return distributedCache.getCacheStats();
    }
}
