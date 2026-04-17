package com.hackathon.search.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchRequest(
        String text,
        String searchText,
        List<String> variants,
        List<String> hyde,
        List<String> keywords,
        String asker,
        Long askedOn,
        Object dateRange,
        List<Object> dateMentions,
        Map<String, List<String>> entities,
        Integer topK,
        String chatId
) {}
