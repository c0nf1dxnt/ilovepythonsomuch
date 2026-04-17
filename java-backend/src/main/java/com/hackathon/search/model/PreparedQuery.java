package com.hackathon.search.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PreparedQuery(
        String normalizedText,
        List<Double> denseVec,
        Map<String, Double> lexicalWeights,
        List<String> keywords,
        Map<String, List<String>> entities
) {}
