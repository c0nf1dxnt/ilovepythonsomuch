package com.hackathon.search.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IndexRequest(
        Chat chat,
        List<Message> messages,
        List<Message> overlapMessages,
        List<Message> newMessages
) {}
