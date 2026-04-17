package com.hackathon.search.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hackathon.search.config.AppProperties;
import com.hackathon.search.model.PreparedChunk;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class QdrantService {

    private final WebClient qdrantClient;
    private final AppProperties props;

    private String collection() {
        return props.qdrant().collection();
    }

    @PostConstruct
    public void ensureCollection() {
        try {
            qdrantClient.get()
                    .uri("/collections/{name}", collection())
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("Qdrant collection '{}' already exists", collection());
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatusCode.valueOf(404)) {
                createCollection();
            } else {
                log.warn("Failed to check collection; will try to create. Error: {}", e.getMessage());
                createCollection();
            }
        } catch (Exception e) {
            log.warn("Qdrant not reachable yet: {}. Collection will be created on first upsert.", e.getMessage());
        }
    }

    private void createCollection() {
        Map<String, Object> body = Map.of(
                "vectors", Map.of(
                        "size", props.qdrant().vectorSize(),
                        "distance", "Cosine"
                )
        );
        try {
            qdrantClient.put()
                    .uri("/collections/{name}", collection())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("Created Qdrant collection '{}'", collection());
        } catch (Exception e) {
            log.error("Failed to create Qdrant collection '{}': {}", collection(), e.getMessage());
        }
    }

    public int upsert(List<PreparedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return 0;

        List<Map<String, Object>> points = new ArrayList<>(chunks.size());
        for (PreparedChunk c : chunks) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", c.chatId());
            payload.put("chunk_id", c.chunkId());
            payload.put("message_ids", c.messageIds());
            payload.put("chunk_text", c.chunkText());
            payload.put("sender_ids", c.senderIds());
            payload.put("time_from", c.timeFrom());
            payload.put("time_to", c.timeTo());
            if (c.entities() != null) payload.put("entities", c.entities());
            if (c.lexicalWeights() != null) payload.put("lexical_weights", c.lexicalWeights());

            points.add(Map.of(
                    "id", c.chunkId(),
                    "vector", c.denseVec(),
                    "payload", payload
            ));
        }

        qdrantClient.put()
                .uri(uri -> uri.path("/collections/{name}/points").queryParam("wait", "true").build(collection()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("points", points))
                .retrieve()
                .toBodilessEntity()
                .block();

        return points.size();
    }

    public List<SearchPoint> search(List<Double> vector, int limit, String chatIdFilter) {
        Map<String, Object> body = new HashMap<>();
        body.put("vector", vector);
        body.put("limit", limit);
        body.put("with_payload", true);

        if (chatIdFilter != null && !chatIdFilter.isBlank()) {
            body.put("filter", Map.of(
                    "must", List.of(Map.of(
                            "key", "chat_id",
                            "match", Map.of("value", chatIdFilter)
                    ))
            ));
        }

        SearchResponse resp = qdrantClient.post()
                .uri("/collections/{name}/points/search", collection())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(SearchResponse.class)
                .block();

        if (resp == null || resp.result() == null) return Collections.emptyList();
        return resp.result();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchPoint(Object id, double score, Map<String, Object> payload) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchResponse(List<SearchPoint> result) {}
}
