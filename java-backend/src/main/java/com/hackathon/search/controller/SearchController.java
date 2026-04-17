package com.hackathon.search.controller;

import com.hackathon.search.model.IndexRequest;
import com.hackathon.search.model.SearchRequest;
import com.hackathon.search.model.SearchResponse;
import com.hackathon.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/index")
    public ResponseEntity<Map<String, Object>> index(@RequestBody IndexRequest request) {
        int indexed = searchService.index(request);
        return ResponseEntity.ok(Map.of("status", "ok", "indexed_chunks", indexed));
    }

    @PostMapping("/search")
    public SearchResponse search(@RequestBody SearchRequest request) {
        return new SearchResponse(searchService.search(request));
    }
}
