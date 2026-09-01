package com.example.microloan.knowledge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "knowledge.ingestion.classpath-bootstrap-enabled", havingValue = "true")
public class KnowledgeClasspathImporter implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeClasspathImporter.class);

    private final ResourcePatternResolver resourceResolver;
    private final KnowledgeImportService importService;
    private final int maxDocumentBytes;

    public KnowledgeClasspathImporter(
            ResourcePatternResolver resourceResolver,
            KnowledgeImportService importService,
            @Value("${knowledge.ingestion.markdown-max-bytes:1048576}") int maxDocumentBytes) {
        if (maxDocumentBytes <= 0) {
            throw new IllegalArgumentException("Markdown 文档大小上限必须大于 0");
        }
        this.resourceResolver = resourceResolver;
        this.importService = importService;
        this.maxDocumentBytes = maxDocumentBytes;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Resource[] resources = resourceResolver.getResources("classpath*:knowledge/*.md");
        Arrays.sort(resources, Comparator.comparing(Resource::getFilename,
                Comparator.nullsLast(Comparator.naturalOrder())));
        for (Resource resource : resources) {
            KnowledgeImportResult result = importService.importMarkdownDraft(readUtf8(resource));
            LOGGER.info("Imported classpath knowledge {} version {} with {} chunks (duplicate={})",
                    result.documentNo(), result.version(), result.chunkCount(), result.duplicate());
        }
    }

    private String readUtf8(Resource resource) throws IOException {
        try (InputStream input = resource.getInputStream()) {
            byte[] content = input.readNBytes(maxDocumentBytes + 1);
            if (content.length > maxDocumentBytes) {
                throw new IllegalArgumentException("知识文档超过大小上限：" + resource.getFilename());
            }
            // UTF-8 是仓库知识资产的唯一编码，避免不同部署环境产生不同校验和。
            return new String(content, StandardCharsets.UTF_8);
        }
    }
}
