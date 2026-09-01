package com.example.microloan.knowledge;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeReranker {

    public List<KnowledgeEvidence> rerank(String searchText, List<KnowledgeEvidence> candidates, int limit) {
        List<String> terms = Arrays.stream(searchText.strip().split("\\s+"))
                .filter(term -> !term.isBlank())
                .distinct()
                .toList();
        // 词项覆盖只用于候选集内重排，不能绕过发布状态、受众、产品和有效期过滤。
        return candidates.stream()
                .sorted(Comparator.comparingInt((KnowledgeEvidence evidence) -> coverage(evidence, terms))
                        .reversed()
                        .thenComparing(Comparator.comparingDouble(KnowledgeEvidence::score).reversed()))
                .limit(limit)
                .toList();
    }

    private int coverage(KnowledgeEvidence evidence, List<String> terms) {
        String searchable = evidence.title() + "\n" + evidence.sectionPath() + "\n" + evidence.content();
        return (int) terms.stream().filter(searchable::contains).count();
    }
}
