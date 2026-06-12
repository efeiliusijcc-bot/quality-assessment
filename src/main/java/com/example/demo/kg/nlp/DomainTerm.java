package com.example.demo.kg.nlp;

import java.util.List;
import java.util.UUID;

/**
 * Domain lexicon entry used by Jieba/entity extraction.
 * type examples: PROCESS_STEP, PARAMETER, QUALITY_METRIC, DEFECT_TYPE, EQUIPMENT, WORKSTATION.
 */
public record DomainTerm(
        String term,
        String type,
        UUID refId,
        String refSchema,
        String refTable,
        String code,
        List<String> aliases,
        String source
) {
    public DomainTerm {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        source = source == null ? "UNKNOWN" : source;
    }
}
