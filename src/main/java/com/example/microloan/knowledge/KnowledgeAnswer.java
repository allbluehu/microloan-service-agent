package com.example.microloan.knowledge;

import java.util.List;

public record KnowledgeAnswer(String answer, List<KnowledgeEvidence> sources) {}
