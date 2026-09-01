package com.example.microloan.knowledge;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeMarkdownParser {

    private static final Set<String> AUDIENCES = Set.of("PUBLIC", "CUSTOMER", "INTERNAL");

    public KnowledgeMarkdownDocument parse(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            throw new IllegalArgumentException("知识文档不能为空");
        }
        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        if (lines.length < 3 || !"---".equals(lines[0].strip())) {
            throw new IllegalArgumentException("知识文档必须以 --- 元数据头开始");
        }

        int metadataEnd = findMetadataEnd(lines);
        Map<String, String> metadata = parseMetadata(lines, metadataEnd);
        String audience = required(metadata, "audience").toUpperCase();
        if (!AUDIENCES.contains(audience)) {
            throw new IllegalArgumentException("audience 仅支持 PUBLIC、CUSTOMER 或 INTERNAL");
        }
        LocalDate effectiveFrom = parseDate(required(metadata, "effectiveFrom"), "effectiveFrom");
        LocalDate effectiveTo = optionalDate(metadata.get("effectiveTo"), "effectiveTo");
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo 不能早于 effectiveFrom");
        }

        String body = String.join("\n", Arrays.copyOfRange(lines, metadataEnd + 1, lines.length)).strip();
        if (body.isBlank()) {
            throw new IllegalArgumentException("知识文档正文不能为空");
        }
        return new KnowledgeMarkdownDocument(
                required(metadata, "documentNo"), required(metadata, "title"),
                required(metadata, "productCode"), required(metadata, "region"),
                required(metadata, "version"), audience, effectiveFrom, effectiveTo,
                required(metadata, "reviewer"), parseKeywords(required(metadata, "keywords")),
                body, KnowledgeChecksum.sha256(normalized));
    }

    private int findMetadataEnd(String[] lines) {
        for (int index = 1; index < lines.length; index++) {
            if ("---".equals(lines[index].strip())) {
                return index;
            }
        }
        throw new IllegalArgumentException("知识文档元数据头缺少结束标记 ---");
    }

    private Map<String, String> parseMetadata(String[] lines, int metadataEnd) {
        Map<String, String> metadata = new LinkedHashMap<>();
        for (int index = 1; index < metadataEnd; index++) {
            String line = lines[index].strip();
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int separator = line.indexOf(':');
            if (separator <= 0) {
                throw new IllegalArgumentException("无效的知识元数据行：" + line);
            }
            String previous = metadata.put(line.substring(0, separator).strip(), line.substring(separator + 1).strip());
            if (previous != null) {
                throw new IllegalArgumentException("知识元数据字段重复：" + line.substring(0, separator).strip());
            }
        }
        return metadata;
    }

    private String required(Map<String, String> metadata, String field) {
        String value = metadata.get(field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少知识元数据：" + field);
        }
        return value;
    }

    private List<String> parseKeywords(String value) {
        List<String> keywords = Arrays.stream(value.split(","))
                .map(String::strip)
                .filter(keyword -> !keyword.isBlank())
                .distinct()
                .toList();
        if (keywords.isEmpty()) {
            throw new IllegalArgumentException("keywords 至少包含一个关键词");
        }
        return keywords;
    }

    private LocalDate optionalDate(String value, String field) {
        return value == null || value.isBlank() ? null : parseDate(value, field);
    }

    private LocalDate parseDate(String value, String field) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(field + " 必须使用 yyyy-MM-dd 格式", exception);
        }
    }
}
