package com.example.microloan.knowledge;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface KnowledgeMapper {

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
}
