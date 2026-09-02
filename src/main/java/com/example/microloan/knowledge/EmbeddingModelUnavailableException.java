package com.example.microloan.knowledge;

public class EmbeddingModelUnavailableException extends RuntimeException {
    public EmbeddingModelUnavailableException() {
        super("向量模型未启用，请先配置 AI_EMBEDDING_MODEL");
    }
}
