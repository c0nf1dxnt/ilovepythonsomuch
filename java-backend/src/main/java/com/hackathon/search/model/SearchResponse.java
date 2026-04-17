package com.hackathon.search.model;

import java.util.List;

public record SearchResponse(List<SearchHit> results) {}
