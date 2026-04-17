package com.hackathon.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Python python,
        Qdrant qdrant,
        Http http,
        Search search
) {
    public record Python(String baseUrl) {}

    public record Qdrant(String baseUrl, String collection, int vectorSize) {}

    public record Http(int connectTimeoutMs, int readTimeoutMs) {}

    public record Search(int defaultTopK, int candidateMultiplier) {}
}
