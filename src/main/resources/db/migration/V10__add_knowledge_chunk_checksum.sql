ALTER TABLE knowledge_chunk ADD COLUMN checksum VARCHAR(64);

-- 历史种子切片使用数据库摘要回填；新导入切片统一写入 SHA-256。
UPDATE knowledge_chunk SET checksum = md5(content) WHERE checksum IS NULL;

ALTER TABLE knowledge_chunk ALTER COLUMN checksum SET NOT NULL;
