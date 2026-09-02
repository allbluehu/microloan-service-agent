package com.example.microloan.agent;

import java.util.List;
import org.springframework.stereotype.Component;

/** 最终 HTTP 响应与持久化之前的统一确定性输出护栏。 */
@Component
public class OutputGuardrail {

    private static final int MAX_ANSWER_LENGTH = 2000;
    private static final List<String> INTERNAL_MARKERS = List.of(
            "java.lang.", "org.springframework.", "SQLException", "SELECT * FROM", "system prompt");

    /**
     * 对最终文案执行敏感数字遮蔽、长度限制和内部实现泄漏检查。
     * 业务卡片保持强类型原值；其中允许暴露的字段由各领域 DTO 白名单控制。
     */
    public GuardrailResult apply(AgentState state) {
        String answer = state.finalAnswer() == null ? "" : state.finalAnswer();
        if (containsInternalMarker(answer)) {
            String replacement = "当前暂时无法安全展示处理结果，请稍后重试或转人工客服。";
            return new GuardrailResult(state.complete(state.toolResult(), replacement), true, "INTERNAL_CONTENT");
        }
        String sanitized = SensitiveTextSanitizer.redactAndLimit(answer, MAX_ANSWER_LENGTH);
        boolean changed = !sanitized.equals(answer);
        return new GuardrailResult(state.complete(state.toolResult(), sanitized), changed,
                changed ? "SENSITIVE_OR_OVERSIZED" : "PASSED");
    }

    private boolean containsInternalMarker(String answer) {
        return INTERNAL_MARKERS.stream().anyMatch(answer::contains);
    }

    public record GuardrailResult(
            /** 经过输出护栏处理的最终状态。 */
            AgentState state,
            /** 最终文案是否被护栏替换或遮蔽。 */
            boolean changed,
            /** 可用于 Trace 的低基数判定原因。 */
            String reason
    ) {}
}
