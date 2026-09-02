package com.example.microloan.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 对话持久化前的数据最小化测试。
 * 这里直接测试 ChatHistoryService 的纯清洗边界，不需要 Mapper；生产调用链为
 * AgentOrchestrator → ChatHistoryService → sanitize → ChatMessageMapper。
 */
class ChatHistoryServiceTest {
    private final ChatHistoryService service = new ChatHistoryService(null);

    @Test
    void redactsLongDigitSequencesBeforePersistence() {
        // 长数字可能是证件号或银行卡号，四位尾号属于业务展示所需信息，应保留可用性。
        assertThat(service.sanitize("身份证 110101199001011234，尾号 1234"))
                .isEqualTo("身份证 [REDACTED]，尾号 1234");
    }

    @Test
    void limitsPersistedContentLength() {
        // 限长同时约束数据库空间、Trace/日志放大风险及后续上下文恢复成本。
        assertThat(service.sanitize("问".repeat(2100))).hasSize(2000);
    }
}
