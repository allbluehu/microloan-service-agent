package com.example.microloan.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class AgentRoutingEvaluationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IntentClassifier intentClassifier = new IntentClassifier(
            new StaticListableBeanFactory().getBeanProvider(org.springframework.ai.chat.model.ChatModel.class),
            objectMapper, new ModelCallExecutor(java.time.Duration.ofSeconds(3)));
    private final RiskClassifier riskClassifier = new RiskClassifier();
    private final CompositeIntentDetector compositeIntentDetector = new CompositeIntentDetector();

    @Test
    void routingBaselineMeetsRiskClarificationAndIntentGates() throws Exception {
        JsonNode cases;
        try (InputStream input = getClass().getResourceAsStream("/evaluation/agent-routing-baseline.json")) {
            cases = objectMapper.readTree(input);
        }
        assertThat(cases).hasSize(100);

        for (JsonNode evaluationCase : cases) {
            String question = evaluationCase.path("question").asText();
            RiskLevel actualRisk = riskClassifier.classify(question).level();
            assertThat(actualRisk)
                    .as("risk classification for: %s", question)
                    .isEqualTo(RiskLevel.valueOf(evaluationCase.path("expectedRisk").asText()));
            if (actualRisk == RiskLevel.HIGH) {
                // 高风险在编排器中先于意图分类接管，评测不再要求普通意图标签。
                continue;
            }

            boolean clarification = compositeIntentDetector.requiresClarification(question);
            assertThat(clarification)
                    .as("composite intent detection for: %s", question)
                    .isEqualTo(evaluationCase.path("expectClarification").asBoolean());
            if (!clarification) {
                assertThat(intentClassifier.classify(question))
                        .as("intent classification for: %s", question)
                        .isEqualTo(Intent.valueOf(evaluationCase.path("expectedIntent").asText()));
            }
        }
    }
}
