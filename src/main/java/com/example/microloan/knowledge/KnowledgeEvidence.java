package com.example.microloan.knowledge;

public record KnowledgeEvidence(
        String documentId,
        String chunkId,
        String title,
        String sectionPath,
        String productCode,
        String version,
        String content,
        double score
) {}
