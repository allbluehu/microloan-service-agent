package com.example.microloan.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class IntentClassifierTest {

    private final IntentClassifier classifier = new IntentClassifier(
            new StaticListableBeanFactory().getBeanProvider(org.springframework.ai.chat.model.ChatModel.class));

    @Test
    void recognizesNextRepaymentQuestionWithoutAConfiguredModel() {
        assertThat(classifier.classify("我下个月要还多少钱？")).isEqualTo(Intent.NEXT_REPAYMENT_QUERY);
    }

    @Test
    void keepsUnsupportedIntentUnknown() {
        assertThat(classifier.classify("怎么申请贷款？")).isEqualTo(Intent.UNKNOWN);
    }

    @Test
    void recognizesApplicationStatusQuestionWithoutAConfiguredModel() {
        assertThat(classifier.classify("我的贷款申请进度到哪了？"))
                .isEqualTo(Intent.APPLICATION_STATUS_QUERY);
    }

    @Test
    void recognizesPaymentHistoryQuestionWithoutAConfiguredModel() {
        assertThat(classifier.classify("查一下这笔贷款的还款记录"))
                .isEqualTo(Intent.PAYMENT_RECORDS_QUERY);
    }

    @Test
    void recognizesEarlySettlementQuestionWithoutAConfiguredModel() {
        assertThat(classifier.classify("9月3日提前结清要多少钱？"))
                .isEqualTo(Intent.EARLY_SETTLEMENT_QUOTE);
    }

    @Test
    void recognizesExplicitHumanHandoffRequest() {
        assertThat(classifier.classify("请直接帮我转人工客服"))
                .isEqualTo(Intent.HUMAN_HANDOFF);
    }

    @Test
    void recognizesProductKnowledgeQuestion() {
        assertThat(classifier.classify("个体工商户经营贷需要什么材料？"))
                .isEqualTo(Intent.KNOWLEDGE_QUERY);
    }

    @Test
    void loadsVersionedPromptFromClasspath() {
        assertThat(IntentClassifier.PROMPT_VERSION).isEqualTo("intent-v1");
        assertThat(classifier.renderPrompt("测试问题"))
                .contains("只输出以下一个标签", "用户输入：测试问题");
    }
}
