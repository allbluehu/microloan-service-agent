package com.example.microloan.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/** 验证风险模型只接受严格受控协议，非法类别和矛盾的 LOW/风险类别组合必须拒绝。 */
class ModelRiskClassifierTest {

    @Test
    void parsesValidHighRiskAndRejectsContradictoryOutput() {
        ModelCallExecutor executor = new ModelCallExecutor(Duration.ofSeconds(1));
        try {
            ModelRiskClassifier classifier = new ModelRiskClassifier(
                    new StaticListableBeanFactory().getBeanProvider(ChatModel.class),
                    new ObjectMapper(), executor, true);

            RiskAssessment valid = classifier.parse(
                    "{\"level\":\"HIGH\",\"category\":\"PERSONAL_SAFETY\",\"confidence\":0.94}");
            assertThat(valid.source()).isEqualTo(RiskSource.MODEL);
            assertThat(valid.level()).isEqualTo(RiskLevel.HIGH);
            assertThat(classifier.parse(
                    "{\"level\":\"LOW\",\"category\":\"COMPLAINT\",\"confidence\":0.99}"))
                    .isNull();
            assertThat(classifier.parse("not-json")).isNull();
        } finally {
            executor.close();
        }
    }
}
