package com.hackathon.search.model;

import java.util.List;

public record SearchHit(
        String chunkId,
        String chatId,
        List<String> messageIds,
        String text,
        double score
) {}
