package com.example.microloan.knowledge;

import java.util.List;

public record KnowledgeAnswer(
        /** 基于合格证据生成或直接返回的答案。 */
        String answer,
        /** 支撑答案的可追溯知识切片。 */
        List<KnowledgeEvidence> sources
) {}
