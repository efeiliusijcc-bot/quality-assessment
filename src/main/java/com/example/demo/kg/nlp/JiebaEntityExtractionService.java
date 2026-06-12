package com.example.demo.kg.nlp;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Entity extraction based on Jieba segmentation + domain lexicon.
 * It combines token matching and longest phrase matching, which is more stable
 * for process/parameter/defect names than pure segmentation.
 */
@Service
public class JiebaEntityExtractionService {

    private final JiebaSegmenterAdapter jieba;
    private final DomainLexiconService lexiconService;

    public JiebaEntityExtractionService(JiebaSegmenterAdapter jieba, DomainLexiconService lexiconService) {
        this.jieba = jieba;
        this.lexiconService = lexiconService;
    }

    public ExtractionResult extract(String text) {
        if (text == null || text.isBlank()) {
            return new ExtractionResult(false, List.of(), List.of());
        }
        List<String> tokens = jieba.segment(text);
        Map<String, ExtractedEntity> dedup = new LinkedHashMap<>();

        // 1) Jieba token exact matching.
        for (String token : tokens) {
            lexiconService.findExact(token).ifPresent(term -> add(dedup, text, term, 0.88, "JIEBA_TOKEN"));
        }

        // 2) Longest lexicon phrase matching. This covers names such as “激光实测功率”.
        Set<String> occupied = new LinkedHashSet<>();
        for (DomainTerm term : lexiconService.allTerms()) {
            String t = term.term();
            if (t == null || t.length() < 2) continue;
            int from = 0;
            while (from < text.length()) {
                int idx = text.indexOf(t, from);
                if (idx < 0) break;
                String spanKey = idx + ":" + (idx + t.length());
                if (!occupied.contains(spanKey)) {
                    occupied.add(spanKey);
                    add(dedup, text, term, 0.96, "DOMAIN_LEXICON_LONGEST_MATCH", idx, idx + t.length());
                }
                from = idx + Math.max(1, t.length());
            }
        }

        return new ExtractionResult(jieba.isJiebaAvailable(), tokens, new ArrayList<>(dedup.values()));
    }

    private void add(Map<String, ExtractedEntity> dedup, String text, DomainTerm term, double confidence, String evidence) {
        int start = text.indexOf(term.term());
        int end = start >= 0 ? start + term.term().length() : -1;
        add(dedup, text, term, confidence, evidence, start, end);
    }

    private void add(Map<String, ExtractedEntity> dedup, String text, DomainTerm term, double confidence, String evidence, int start, int end) {
        String key = term.type() + "|" + term.term() + "|" + term.refId();
        ExtractedEntity current = dedup.get(key);
        if (current == null || confidence > current.confidence()) {
            dedup.put(key, new ExtractedEntity(
                    term.term(),
                    term.type(),
                    term.refId(),
                    term.refSchema(),
                    term.refTable(),
                    term.code(),
                    start,
                    end,
                    confidence,
                    evidence
            ));
        }
    }

    public record ExtractionResult(boolean jiebaAvailable, List<String> tokens, List<ExtractedEntity> entities) {}
}
