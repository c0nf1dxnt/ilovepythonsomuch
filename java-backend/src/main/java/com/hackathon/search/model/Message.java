package com.hackathon.search.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Message(
        String id,
        String text,
        Long time,
        String senderId,
        List<String> fileSnippets,
        List<Object> parts,
        List<Object> mentions,
        String threadSn,
        Object memberEvent,
        Boolean isSystem,
        Boolean isHidden,
        Boolean isForward,
        Boolean isQuote
) {}
