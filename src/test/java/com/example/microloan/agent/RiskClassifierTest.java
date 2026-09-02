package com.example.microloan.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.microloan.ticket.TicketType;
import org.junit.jupiter.api.Test;

/**
 * 高风险规则分类测试。
 * 生产调用链中 RiskClassifier 是 AgentOrchestrator 的第一道业务决策，高风险结果会绕过普通意图模型，
 * 直接创建对应类型的高优先级工单，因此同时断言风险等级和工单类型。
 */
class RiskClassifierTest {

    private final RiskClassifier classifier = new RiskClassifier();

    @Test
    void classifiesIdentityTheftAsHighRisk() {
        // 冒名贷款不能继续进入自助查询或知识回答，必须由人工核验身份和贷款来源。
        RiskAssessment result = classifier.classify("这笔贷款不是我申请的");

        assertThat(result.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(result.ticketType()).isEqualTo(TicketType.IDENTITY_THEFT);
    }

    @Test
    void classifiesThirdPartyDisclosureAsPrivacyRisk() {
        // 向第三方披露债务属于隐私投诉，与一般催收咨询使用不同的工单分类。
        RiskAssessment result = classifier.classify("催收把欠款告诉同事，我要投诉");

        assertThat(result.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(result.ticketType()).isEqualTo(TicketType.PRIVACY_BREACH);
    }
}
