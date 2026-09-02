package com.example.microloan.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 最终输出护栏测试。
 * 调用链为“路由执行完成 → OutputGuardrail → 同一安全状态写入聊天记录并返回 HTTP”，因此这里同时验证
 * 敏感数字遮蔽和内部实现泄漏整段替换，防止只在数据库侧脱敏而原始响应仍暴露。
 */
class OutputGuardrailTest {

    private final OutputGuardrail guardrail = new OutputGuardrail();

    @Test
    void redactsSensitiveDigitsWithoutDroppingStructuredResult() {
        ToolResult result = new ToolResult.Knowledge(new com.example.microloan.knowledge.KnowledgeAnswer(
                "请联系 13800138000", java.util.List.of()));
        AgentState original = state(result, "请联系 13800138000 继续处理");

        OutputGuardrail.GuardrailResult guarded = guardrail.apply(original);

        assertThat(guarded.changed()).isTrue();
        assertThat(guarded.reason()).isEqualTo("SENSITIVE_OR_OVERSIZED");
        assertThat(guarded.state().finalAnswer()).doesNotContain("13800138000").contains("[REDACTED]");
        assertThat(guarded.state().toolResult()).isSameAs(result);
    }

    @Test
    void replacesInternalImplementationDetailsWithSafeMessage() {
        OutputGuardrail.GuardrailResult guarded = guardrail.apply(
                state(null, "org.springframework.jdbc.SQLException: SELECT * FROM customer"));

        assertThat(guarded.changed()).isTrue();
        assertThat(guarded.reason()).isEqualTo("INTERNAL_CONTENT");
        assertThat(guarded.state().finalAnswer()).isEqualTo("当前暂时无法安全展示处理结果，请稍后重试或转人工客服。");
    }

    private AgentState state(ToolResult result, String answer) {
        return new AgentState("trace", "session", "message", "content", null, null, null, "ALL", null,
                RiskLevel.LOW, null, Intent.KNOWLEDGE_QUERY, result, answer);
    }
}
