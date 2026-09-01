package com.example.microloan.knowledge;

import java.util.StringJoiner;

final class KnowledgeVectorCodec {

    private KnowledgeVectorCodec() {}

    static String encode(float[] vector, int expectedDimensions) {
        if (vector.length != expectedDimensions) {
            throw new IllegalStateException(
                    "Embedding dimension mismatch: expected %d but got %d"
                            .formatted(expectedDimensions, vector.length));
        }
        StringJoiner encoded = new StringJoiner(",", "[", "]");
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw new IllegalStateException("Embedding contains a non-finite value");
            }
            encoded.add(Float.toString(value));
        }
        return encoded.toString();
    }
}
