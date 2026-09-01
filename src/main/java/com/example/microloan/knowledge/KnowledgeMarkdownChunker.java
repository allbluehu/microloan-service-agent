package com.example.microloan.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeMarkdownChunker {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$");

    private final int maxCharacters;

    public KnowledgeMarkdownChunker(@Value("${knowledge.ingestion.chunk-max-characters:800}") int maxCharacters) {
        if (maxCharacters < 100) {
            throw new IllegalArgumentException("切片最大字符数不能小于 100");
        }
        this.maxCharacters = maxCharacters;
    }

    public List<KnowledgeChunkDraft> chunk(KnowledgeMarkdownDocument document) {
        List<ContentBlock> blocks = extractBlocks(document.body());
        List<KnowledgeChunkDraft> chunks = new ArrayList<>();
        String currentPath = null;
        StringBuilder currentContent = new StringBuilder();

        for (ContentBlock block : blocks) {
            for (String part : splitOversized(block.content())) {
                boolean sectionChanged = currentPath != null && !currentPath.equals(block.sectionPath());
                boolean exceedsLimit = !currentContent.isEmpty()
                        && currentContent.length() + 2 + part.length() > maxCharacters;
                if (sectionChanged || exceedsLimit) {
                    addChunk(chunks, document, currentPath, currentContent.toString());
                    currentContent.setLength(0);
                }
                currentPath = block.sectionPath();
                if (!currentContent.isEmpty()) {
                    currentContent.append("\n\n");
                }
                currentContent.append(part);
            }
        }
        if (!currentContent.isEmpty()) {
            addChunk(chunks, document, currentPath, currentContent.toString());
        }
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("知识文档没有可切分的正文段落");
        }
        return List.copyOf(chunks);
    }

    private List<ContentBlock> extractBlocks(String body) {
        List<ContentBlock> blocks = new ArrayList<>();
        List<String> headingPath = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        String paragraphPath = "正文";

        for (String rawLine : body.split("\n", -1)) {
            Matcher heading = HEADING.matcher(rawLine.strip());
            if (heading.matches()) {
                flushBlock(blocks, paragraphPath, paragraph);
                int level = heading.group(1).length();
                while (headingPath.size() >= level) {
                    headingPath.removeLast();
                }
                while (headingPath.size() < level - 1) {
                    // 跳级标题用空占位保持层级稳定，路径输出时会忽略占位。
                    headingPath.add("");
                }
                headingPath.add(heading.group(2).strip());
                paragraphPath = sectionPath(headingPath);
                continue;
            }
            if (rawLine.isBlank()) {
                flushBlock(blocks, paragraphPath, paragraph);
                continue;
            }
            if (!paragraph.isEmpty()) {
                paragraph.append('\n');
            }
            paragraph.append(rawLine.stripTrailing());
        }
        flushBlock(blocks, paragraphPath, paragraph);
        return blocks;
    }

    private void flushBlock(List<ContentBlock> blocks, String sectionPath, StringBuilder paragraph) {
        if (!paragraph.isEmpty()) {
            blocks.add(new ContentBlock(sectionPath, paragraph.toString().strip()));
            paragraph.setLength(0);
        }
    }

    private String sectionPath(List<String> headings) {
        String path = headings.stream().filter(heading -> !heading.isBlank())
                .reduce((left, right) -> left + "/" + right).orElse("正文");
        return path.isBlank() ? "正文" : path;
    }

    private List<String> splitOversized(String content) {
        List<String> parts = new ArrayList<>();
        String remaining = content;
        while (remaining.length() > maxCharacters) {
            int breakAt = findSemanticBoundary(remaining, maxCharacters);
            parts.add(remaining.substring(0, breakAt).strip());
            remaining = remaining.substring(breakAt).strip();
        }
        if (!remaining.isBlank()) {
            parts.add(remaining);
        }
        return parts;
    }

    private int findSemanticBoundary(String content, int upperBound) {
        int lowerBound = upperBound / 2;
        for (int index = upperBound; index >= lowerBound; index--) {
            char candidate = content.charAt(index - 1);
            if (candidate == '。' || candidate == '！' || candidate == '？'
                    || candidate == '；' || candidate == '\n') {
                return index;
            }
        }
        // 极长且没有句子边界的文本只能硬切，保证数据库与模型输入有确定上限。
        return upperBound;
    }

    private void addChunk(List<KnowledgeChunkDraft> chunks, KnowledgeMarkdownDocument document,
                          String sectionPath, String content) {
        String chunkNo = "KC-%03d".formatted(chunks.size() + 1);
        String searchText = String.join(" ", document.productCode(), String.join(" ", document.keywords()),
                document.title(), sectionPath, content);
        chunks.add(new KnowledgeChunkDraft(chunkNo, sectionPath, content, searchText,
                KnowledgeChecksum.sha256(content)));
    }

    private record ContentBlock(
            /** 段落所属的标题层级路径。 */
            String sectionPath,
            /** 尚未合并为最终切片的原始段落。 */
            String content
    ) {}
}
