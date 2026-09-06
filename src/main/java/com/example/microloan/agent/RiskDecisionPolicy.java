package com.example.microloan.agent;

import com.example.microloan.ticket.TicketType;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 合并规则和模型风险结果，确保模型结果永远不能降低规则已经识别的风险。 */
@Component
public class RiskDecisionPolicy {

    private final double confidenceThreshold;

    public RiskDecisionPolicy(@Value("${agent.risk.model-confidence-threshold:0.75}") double confidenceThreshold) {
        if (confidenceThreshold < 0 || confidenceThreshold > 1) {
            throw new IllegalArgumentException("Risk confidence threshold must be between 0 and 1");
        }
        this.confidenceThreshold = confidenceThreshold;
    }

    /**
     * 明确规则 HIGH 优先；达到阈值的模型 HIGH 可以升级风险，模型 LOW 只能确认规则 LOW。
     * 规则 REVIEW 即使遇到高置信度模型 LOW 也不会被降级，最终以 SAFE_FALLBACK 升级人工处理。
     * 模型缺失或低置信度本身不把所有请求都升级：规则 LOW 仍按默认低风险处理。
     */
    public RiskAssessment decide(RiskAssessment rule, Optional<RiskAssessment> model) {
        // 门面已对 HIGH 短路，这里再次保护，保证策略被独立调用时也不能降低明确风险。
        if (rule.level() == RiskLevel.HIGH) {
            return rule;
        }
        if (model.isPresent() && model.orElseThrow().confidence() >= confidenceThreshold) {
            RiskAssessment accepted = model.orElseThrow();
            if (accepted.level() == RiskLevel.HIGH) {
                return accepted;
            }
            if (rule.level() == RiskLevel.LOW) {
                return accepted;
            }
        }
        // 保留弱信号的类别、规则编号和原始置信度；升级的是处置等级，不虚构更高的识别置信度。
        if (rule.level() == RiskLevel.REVIEW) {
            return new RiskAssessment(RiskLevel.HIGH,
                    rule.ticketType() == null ? TicketType.GENERAL_COMPLAINT : rule.ticketType(),
                    rule.category(), RiskSource.SAFE_FALLBACK, rule.confidence(), rule.ruleCode());
        }
        return RiskAssessment.low();
    }
}
