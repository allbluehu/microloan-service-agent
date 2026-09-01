CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE knowledge_chunk
    ADD COLUMN embedding VECTOR(1024),
    ADD COLUMN embedding_model VARCHAR(100),
    ADD COLUMN embedded_at TIMESTAMPTZ;

CREATE INDEX idx_knowledge_chunk_embedding_hnsw
    ON knowledge_chunk USING hnsw (embedding vector_cosine_ops)
    WHERE embedding IS NOT NULL;
