package com.hackathon.search.service;

import com.hackathon.search.config.AppProperties;
import com.hackathon.search.model.IndexRequest;
import com.hackathon.search.model.PreparedChunk;
import com.hackathon.search.model.PreparedQuery;
import com.hackathon.search.model.SearchHit;
import com.hackathon.search.model.SearchRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final PythonClient pythonClient;
    private final QdrantService qdrantService;
    private final AppProperties props;

    public int index(IndexRequest request) {
        List<PreparedChunk> chunks = pythonClient.prepareIndex(request);
        String chatId = request.chat() != null ? request.chat().id() : null;
        if (chunks.isEmpty()) {
            log.info("No chunks produced for chat {}", chatId);
            return 0;
        }
        int upserted = qdrantService.upsert(chunks);
        log.info("Upserted {} chunks for chat {}", upserted, chatId);
        return upserted;
    }

    public List<SearchHit> search(SearchRequest request) {
        int topK = request.topK() != null && request.topK() > 0
                ? request.topK()
                : props.search().defaultTopK();
        int candidates = Math.max(topK * props.search().candidateMultiplier(), topK);

        PreparedQuery pq = pythonClient.prepareQuery(request);
        if (pq == null || pq.denseVec() == null || pq.denseVec().isEmpty()) {
            return List.of();
        }

        List<QdrantService.SearchPoint> points = qdrantService.search(
                pq.denseVec(),
                candidates,
                request.chatId()
        );

        Set<String> rerankTerms = collectRerankTerms(request, pq);

        List<SearchHit> hits = new ArrayList<>(points.size());
        for (QdrantService.SearchPoint p : points) {
            Map<String, Object> payload = p.payload();
            if (payload == null) continue;

            String chunkText = asString(payload.get("chunk_text"));
            double rerank = lexicalBoost(chunkText, rerankTerms);
            double finalScore = p.score() + rerank;

            hits.add(new SearchHit(
                    asString(payload.get("chunk_id")),
                    asString(payload.get("chat_id")),
                    asStringList(payload.get("message_ids")),
                    chunkText,
                    finalScore
            ));
        }

        hits.sort(Comparator.comparingDouble(SearchHit::score).reversed());

        return hits.size() > topK ? hits.subList(0, topK) : hits;
    }

    private Set<String> collectRerankTerms(SearchRequest req, PreparedQuery pq) {
        Set<String> terms = new HashSet<>();
        if (req.keywords() != null) req.keywords().forEach(k -> addTerm(terms, k));
        if (pq != null && pq.keywords() != null) pq.keywords().forEach(k -> addTerm(terms, k));
        if (req.entities() != null) {
            for (List<String> vs : req.entities().values()) {
                if (vs != null) vs.forEach(v -> addTerm(terms, v));
            }
        }
        return terms;
    }

    private void addTerm(Set<String> acc, String term) {
        if (term == null) return;
        String t = term.trim().toLowerCase(Locale.ROOT);
        if (t.length() >= 2) acc.add(t);
    }

    private double lexicalBoost(String text, Set<String> terms) {
        if (text == null || terms.isEmpty()) return 0.0;
        String lower = text.toLowerCase(Locale.ROOT);
        int hits = 0;
        for (String t : terms) {
            if (lower.contains(t)) hits++;
        }
        return hits == 0 ? 0.0 : Math.min(0.05 * hits, 0.25);
    }

    private String asString(Object o) {
        return o == null ? null : o.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object o) {
        if (o instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object v : list) if (v != null) result.add(v.toString());
            return result;
        }
        return List.of();
    }
}
