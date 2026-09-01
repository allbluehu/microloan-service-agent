package com.example.microloan.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatHistoryServiceTest {
    private final ChatHistoryService service = new ChatHistoryService(null);

    @Test
    void redactsLongDigitSequencesBeforePersistence() {
        assertThat(service.sanitize("身份证 110101199001011234，尾号 1234"))
                .isEqualTo("身份证 [REDACTED]，尾号 1234");
    }

    @Test
    void limitsPersistedContentLength() {
        assertThat(service.sanitize("问".repeat(2100))).hasSize(2000);
    }
}
