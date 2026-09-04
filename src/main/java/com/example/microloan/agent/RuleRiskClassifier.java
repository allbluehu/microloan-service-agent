package com.example.microloan.agent;

import com.example.microloan.ticket.TicketType;
import org.springframework.stereotype.Component;

/** 不依赖外部服务的第一层风险规则，负责明确风险和需要进一步复核的弱信号。 */
@Component
public class RuleRiskClassifier {

    public RiskAssessment classify(String content) {
        String normalized = content == null ? "" : content.toLowerCase();
        if (containsAny(normalized, "冒名", "不是我申请", "非本人贷款")) {
            return high(TicketType.IDENTITY_THEFT, RiskCategory.IDENTITY_THEFT, "RISK_IDENTITY_THEFT");
        }
        if (containsAny(normalized, "隐私泄露", "泄露我的信息", "告诉同事", "告诉家人")) {
            return high(TicketType.PRIVACY_BREACH, RiskCategory.PRIVACY_BREACH, "RISK_PRIVACY_BREACH");
        }
        if (normalized.contains("催收") && containsAny(normalized, "威胁", "恐吓", "辱骂", "投诉")) {
            return high(TicketType.COLLECTION_COMPLAINT, RiskCategory.COLLECTION_ABUSE, "RISK_COLLECTION_ABUSE");
        }
        if (containsAny(normalized, "自杀", "不想活", "去死", "跳楼", "杀了", "弄死")) {
            return high(TicketType.GENERAL_COMPLAINT, RiskCategory.PERSONAL_SAFETY, "RISK_PERSONAL_SAFETY");
        }
        if (normalized.contains("投诉")) {
            return high(TicketType.GENERAL_COMPLAINT, RiskCategory.COMPLAINT, "RISK_COMPLAINT");
        }
        // 弱信号不能直接判低风险；模型关闭或失败时由组合策略安全升级为人工处理。
        if (containsAny(normalized, "活着没意思", "楼顶", "监管", "银保监", "报警", "曝光你们")) {
            return new RiskAssessment(RiskLevel.REVIEW, TicketType.GENERAL_COMPLAINT,
                    RiskCategory.COMPLAINT, RiskSource.RULE, 0.5, "RISK_REVIEW_SIGNAL");
        }
        return RiskAssessment.low();
    }

    private RiskAssessment high(TicketType ticketType, RiskCategory category, String ruleCode) {
        return new RiskAssessment(RiskLevel.HIGH, ticketType, category, RiskSource.RULE, 1.0, ruleCode);
    }

    private boolean containsAny(String content, String... terms) {
        for (String term : terms) {
            if (content.contains(term)) {
                return true;
            }
        }
        return false;
    }
}
