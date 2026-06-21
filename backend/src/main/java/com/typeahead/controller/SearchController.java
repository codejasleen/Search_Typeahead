package com.typeahead.controller;

import com.typeahead.model.Suggestion;
import com.typeahead.service.SuggestionService;
import com.typeahead.service.TrendingService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SearchController {

    private final SuggestionService suggestionService;
    private final TrendingService trendingService;

    public SearchController(SuggestionService suggestionService, TrendingService trendingService) {
        this.suggestionService = suggestionService;
        this.trendingService = trendingService;
    }

    @GetMapping("/suggest")
    public List<Suggestion> suggest(@RequestParam String q) {
        return suggestionService.getSuggestions(q);
    }

    @PostMapping("/search")
    public Map<String, String> search(@RequestBody Map<String, String> body) {
        String query = body.get("query");
        suggestionService.recordSearch(query);
        return Map.of(
                "status", "success",
                "message", "Searched successfully",
                "query", query != null ? query : ""
        );
    }

    @GetMapping("/trending")
    public List<Suggestion> trending(@RequestParam(defaultValue = "10") int limit) {
        return trendingService.getTrending(limit);
    }
}
