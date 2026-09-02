package com.example.microloan.agent;

import java.util.regex.Pattern;

final class SensitiveTextSanitizer {
    private static final Pattern SENSITIVE_DIGITS = Pattern.compile("(?<!\\d)\\d{6,}(?!\\d)");

    private SensitiveTextSanitizer() {}

    static String redactAndLimit(String content, int maxLength) {
        if (maxLength < 1) {
            throw new IllegalArgumentException("Maximum text length must be positive");
        }
        String redacted = SENSITIVE_DIGITS.matcher(content == null ? "" : content).replaceAll("[REDACTED]");
        if (redacted.length() <= maxLength) {
            return redacted;
        }
        // 按 Unicode 码点定位截断位置，避免把表情等代理字符拆成无效文本。
        int codePointLimit = Math.min(maxLength, redacted.codePointCount(0, redacted.length()));
        return redacted.substring(0, redacted.offsetByCodePoints(0, codePointLimit));
    }
}
