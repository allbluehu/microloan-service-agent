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

    public RiskAssessment decide(RiskAssessment rule, Optional<RiskAssessment> model) {
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
        if (rule.level() == RiskLevel.REVIEW) {
            return new RiskAssessment(RiskLevel.HIGH,
                    rule.ticketType() == null ? TicketType.GENERAL_COMPLAINT : rule.ticketType(),
                    rule.category(), RiskSource.SAFE_FALLBACK, rule.confidence(), rule.ruleCode());
        }
        return RiskAssessment.low();
    }
}
