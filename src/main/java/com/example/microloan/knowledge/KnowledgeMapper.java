package com.example.microloan.knowledge;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface KnowledgeMapper {

    @Select("""
            INSERT INTO knowledge_document
                (document_no, title, product_code, region, version, audience, status,
                 effective_from, effective_to, reviewer, checksum)
            VALUES
                (#{document.documentNo}, #{document.title}, #{document.productCode}, #{document.region},
                 #{document.version}, #{document.audience}, 'DRAFT', #{document.effectiveFrom},
                 #{document.effectiveTo}, #{document.reviewer}, #{document.checksum})
            ON CONFLICT (document_no, version) DO NOTHING
            RETURNING id
            """)
    Long insertDraftDocument(@Param("document") KnowledgeMarkdownDocument document);

    @Select("""
            SELECT id, document_no, version, checksum, status, reviewer,
                   approved_at, published_at, expired_at
            FROM knowledge_document
            WHERE document_no = #{documentNo} AND version = #{version}
            """)
    KnowledgeDocumentIdentity findDocumentIdentity(@Param("documentNo") String documentNo,
                                                    @Param("version") String version);

    @org.apache.ibatis.annotations.Insert("""
            INSERT INTO knowledge_chunk
                (document_id, chunk_no, section_path, content, search_text, checksum)
            VALUES
                (#{documentId}, #{chunk.chunkNo}, #{chunk.sectionPath}, #{chunk.content},
                 #{chunk.searchText}, #{chunk.checksum})
            """)
    int insertChunk(@Param("documentId") long documentId, @Param("chunk") KnowledgeChunkDraft chunk);

    @Select("SELECT COUNT(*) FROM knowledge_chunk WHERE document_id = #{documentId}")
    int countChunks(@Param("documentId") long documentId);

    @org.apache.ibatis.annotations.Update("""
            UPDATE knowledge_document
            SET status = #{nextStatus},
                reviewer = COALESCE(#{reviewer}, reviewer),
                approved_at = CASE WHEN #{nextStatus} = 'APPROVED' THEN CURRENT_TIMESTAMP ELSE approved_at END,
                published_at = CASE WHEN #{nextStatus} = 'PUBLISHED' THEN CURRENT_TIMESTAMP ELSE published_at END,
                expired_at = CASE WHEN #{nextStatus} = 'EXPIRED' THEN CURRENT_TIMESTAMP ELSE expired_at END
            WHERE document_no = #{documentNo}
              AND version = #{version}
              AND status = #{expectedStatus}
            """)
    int transitionDocumentStatus(@Param("documentNo") String documentNo,
                                 @Param("version") String version,
                                 @Param("expectedStatus") String expectedStatus,
                                 @Param("nextStatus") String nextStatus,
                                 @Param("reviewer") String reviewer);

    @Select("""
            <script>
            SELECT kd.document_no AS document_id, kc.chunk_no AS chunk_id, kd.title,
                   kc.section_path, kd.product_code, kd.version, kc.content,
                   ts_rank_cd(kc.search_vector, plainto_tsquery('simple', #{searchText})) AS score
            FROM knowledge_chunk kc
            JOIN knowledge_document kd ON kd.id = kc.document_id
            WHERE kd.status = 'PUBLISHED'
              AND kd.audience IN ('PUBLIC', 'CUSTOMER')
              AND kd.effective_from &lt;= CURRENT_DATE
              AND (kd.effective_to IS NULL OR kd.effective_to &gt;= CURRENT_DATE)
              AND (kd.region = 'ALL' OR kd.region = #{region})
              <if test="productCode != null">AND kd.product_code = #{productCode}</if>
              AND kc.search_vector @@ plainto_tsquery('simple', #{searchText})
            ORDER BY score DESC, kd.version DESC, kc.chunk_no
            LIMIT #{limit}
            </script>
            """)
    List<KnowledgeEvidence> searchPublished(@Param("searchText") String searchText,
                                            @Param("productCode") String productCode,
                                            @Param("region") String region,
                                            @Param("limit") int limit);

    @Select("""
            <script>
            SELECT kd.document_no AS document_id, kc.chunk_no AS chunk_id, kd.title,
                   kc.section_path, kd.product_code, kd.version, kc.content,
                   1 - (kc.embedding &lt;=&gt; CAST(#{embedding} AS vector)) AS score
            FROM knowledge_chunk kc
            JOIN knowledge_document kd ON kd.id = kc.document_id
            WHERE kd.status = 'PUBLISHED'
              AND kd.audience IN ('PUBLIC', 'CUSTOMER')
              AND kd.effective_from &lt;= CURRENT_DATE
              AND (kd.effective_to IS NULL OR kd.effective_to &gt;= CURRENT_DATE)
              AND (kd.region = 'ALL' OR kd.region = #{region})
              <if test="productCode != null">AND kd.product_code = #{productCode}</if>
              AND kc.embedding IS NOT NULL
            ORDER BY kc.embedding &lt;=&gt; CAST(#{embedding} AS vector), kd.version DESC, kc.chunk_no
            LIMIT #{limit}
            </script>
            """)
    List<KnowledgeEvidence> searchPublishedByVector(@Param("embedding") String embedding,
                                                    @Param("productCode") String productCode,
                                                    @Param("region") String region,
                                                    @Param("limit") int limit);

    @Select("""
            SELECT id, content
            FROM knowledge_chunk
            WHERE embedding IS NULL OR embedding_model != #{modelName}
            ORDER BY id
            """)
    List<KnowledgeChunkForEmbedding> findChunksRequiringEmbedding(@Param("modelName") String modelName);

    @org.apache.ibatis.annotations.Update("""
            UPDATE knowledge_chunk
            SET embedding = CAST(#{embedding} AS vector),
                embedding_model = #{modelName},
                embedded_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateEmbedding(@Param("id") long id,
                        @Param("embedding") String embedding,
                        @Param("modelName") String modelName);
}
