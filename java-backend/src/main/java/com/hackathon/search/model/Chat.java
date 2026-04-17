package com.hackathon.search.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Chat(
        String id,
        String name,
        String sn,
        String type,
        Boolean isPublic,
        Integer membersCount,
        List<Object> members
) {}
