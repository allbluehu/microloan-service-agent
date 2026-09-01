package com.example.microloan.agent;

import com.example.microloan.ticket.TicketType;
import org.springframework.stereotype.Component;

@Component
public class RiskClassifier {

    public RiskAssessment classify(String content) {
        String normalized = content.toLowerCase();
        if (containsAny(normalized, "冒名", "不是我申请", "非本人贷款")) {
            return new RiskAssessment(RiskLevel.HIGH, TicketType.IDENTITY_THEFT);
        }
        if (containsAny(normalized, "隐私泄露", "泄露我的信息", "告诉同事", "告诉家人")) {
            return new RiskAssessment(RiskLevel.HIGH, TicketType.PRIVACY_BREACH);
        }
        if (normalized.contains("催收") && containsAny(normalized, "威胁", "恐吓", "辱骂", "投诉")) {
            return new RiskAssessment(RiskLevel.HIGH, TicketType.COLLECTION_COMPLAINT);
        }
        if (normalized.contains("投诉")) {
            return new RiskAssessment(RiskLevel.HIGH, TicketType.GENERAL_COMPLAINT);
        }
        return RiskAssessment.low();
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
