package com.example.microloan.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.microloan.ticket.TicketType;
import org.junit.jupiter.api.Test;

class RiskClassifierTest {

    private final RiskClassifier classifier = new RiskClassifier();

    @Test
    void classifiesIdentityTheftAsHighRisk() {
        RiskAssessment result = classifier.classify("这笔贷款不是我申请的");

        assertThat(result.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(result.ticketType()).isEqualTo(TicketType.IDENTITY_THEFT);
    }

    @Test
    void classifiesThirdPartyDisclosureAsPrivacyRisk() {
        RiskAssessment result = classifier.classify("催收把欠款告诉同事，我要投诉");

        assertThat(result.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(result.ticketType()).isEqualTo(TicketType.PRIVACY_BREACH);
    }
}
