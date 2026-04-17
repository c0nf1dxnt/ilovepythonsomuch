package com.hackathon.search.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hackathon.search.model.IndexRequest;
import com.hackathon.search.model.PreparedChunk;
import com.hackathon.search.model.PreparedQuery;
import com.hackathon.search.model.SearchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component("pythonServiceClient")
@RequiredArgsConstructor
public class PythonClient {

    private final WebClient pythonClient;

    public List<PreparedChunk> prepareIndex(IndexRequest req) {
        Map<String, Object> body = new HashMap<>();
        body.put("chat", req.chat());
        body.put("messages", req.messages() != null ? req.messages() : Collections.emptyList());
        if (req.overlapMessages() != null) body.put("overlap_messages", req.overlapMessages());
        if (req.newMessages() != null) body.put("new_messages", req.newMessages());

        PrepareIndexResponse resp = pythonClient.post()
                .uri("/prepare_index")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(PrepareIndexResponse.class)
                .block();

        return resp != null && resp.chunks() != null ? resp.chunks() : Collections.emptyList();
    }

    public PreparedQuery prepareQuery(SearchRequest req) {
        Map<String, Object> body = new HashMap<>();
        if (req.text() != null) body.put("text", req.text());
        if (req.searchText() != null) body.put("search_text", req.searchText());
        if (req.variants() != null) body.put("variants", req.variants());
        if (req.hyde() != null) body.put("hyde", req.hyde());
        if (req.keywords() != null) body.put("keywords", req.keywords());
        if (req.asker() != null) body.put("asker", req.asker());
        if (req.askedOn() != null) body.put("asked_on", req.askedOn());
        if (req.dateRange() != null) body.put("date_range", req.dateRange());
        if (req.dateMentions() != null) body.put("date_mentions", req.dateMentions());
        if (req.entities() != null) body.put("entities", req.entities());

        return pythonClient.post()
                .uri("/prepare_query")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(PreparedQuery.class)
                .block();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PrepareIndexResponse(List<PreparedChunk> chunks) {}
}
