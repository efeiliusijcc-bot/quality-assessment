package com.example.demo.kg.nlp;

import java.util.UUID;

/** Extracted entity with source span and confidence. */
public record ExtractedEntity(
        String text,
        String entityType,
        UUID refId,
        String refSchema,
        String refTable,
        String code,
        int startOffset,
        int endOffset,
        double confidence,
        String evidence
) {}
