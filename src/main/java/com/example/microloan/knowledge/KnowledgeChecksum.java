package com.example.microloan.knowledge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class KnowledgeChecksum {

    private KnowledgeChecksum() {}

    static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            // Java 21 必须提供 SHA-256；缺失意味着运行时环境不可用，而不是可恢复的业务错误。
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
