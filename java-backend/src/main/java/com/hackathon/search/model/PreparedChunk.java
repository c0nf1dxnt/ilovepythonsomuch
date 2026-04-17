package com.hackathon.search.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PreparedChunk(
        String chatId,
        String chunkId,
        List<String> messageIds,
        String chunkText,
        List<String> senderIds,
        Long timeFrom,
        Long timeTo,
        List<Map<String, Object>> entities,
        List<Double> denseVec,
        Map<String, Double> lexicalWeights
) {}
