/**
 * 知识导入、治理、向量索引和客户检索模块。
 *
 * <h2>客户知识问答链</h2>
 * <ol>
 *   <li>{@code AgentOrchestrator} 仅在意图为 KNOWLEDGE_QUERY 时调用 {@link KnowledgeService#answer}。</li>
 *   <li>{@link KnowledgeQueryNormalizer} 将自然语言和会话产品/地区槽位整理为受控 {@link KnowledgeQuery}。</li>
 *   <li>{@link KnowledgeRetriever} 通过 {@link KnowledgeMapper} 执行发布状态、受众、生效期、产品和地区过滤；
 *       向量检索启用时并行语义召回，再使用 RRF 融合和 {@link KnowledgeReranker} 确定性重排。</li>
 *   <li>没有合格证据时 {@code KnowledgeService} 拒答；有证据时返回带引用的 {@link KnowledgeAnswer}。</li>
 * </ol>
 *
 * <h2>管理与发布链</h2>
 * <ol>
 *   <li>{@link KnowledgeAdminController} 由独立管理密钥保护，不接受客户 JWT 代替管理员身份。</li>
 *   <li>Markdown/PDF 经解析、元数据校验、语义切片和校验和计算后，由
 *       {@link KnowledgeImportService} 以 DRAFT 状态事务化写入。</li>
 *   <li>{@link KnowledgePublicationService} 通过带期望状态的原子更新实施
 *       DRAFT → APPROVED → PUBLISHED → EXPIRED，未发布版本永不进入客户召回。</li>
 *   <li>{@link KnowledgeEmbeddingService} 查找缺失或模型版本过旧的切片，逐条调用 embedding 模型并写入
 *       pgvector；已成功切片不会重复处理，因此中断或限流后可安全重试。</li>
 * </ol>
 */
package com.example.microloan.knowledge;
