package com.example.microloan.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/**
 * 离线 Agent 路由基线评测。
 * 调用链与生产编排一致地遵循“风险识别 → 复合意图澄清 → 普通意图分类”，但不启动 Spring、数据库或外部模型，
 * 用 100 条固定样本保护高风险优先级、单目标执行和规则降级三个确定性边界。
 */
class AgentRoutingEvaluationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IntentClassifier intentClassifier = new IntentClassifier(
            new StaticListableBeanFactory().getBeanProvider(org.springframework.ai.chat.model.ChatModel.class),
            objectMapper, new ModelCallExecutor(java.time.Duration.ofSeconds(3)));
    private final RiskClassifier riskClassifier = new RiskClassifier(
            new RuleRiskClassifier(), new RiskDecisionPolicy(0.75));
    private final CompositeIntentDetector compositeIntentDetector = new CompositeIntentDetector();

    @Test
    void routingBaselineMeetsRiskClarificationAndIntentGates() throws Exception {
        // 数据集是版本化质量契约；数量变化必须显式修改断言，防止样本被误删后门槛静默变宽。
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
                // 只有未被前置节点接管的消息才进入 IntentClassifier，这与 AgentOrchestrator 的真实调用顺序一致。
                assertThat(intentClassifier.classify(question))
                        .as("intent classification for: %s", question)
                        .isEqualTo(Intent.valueOf(evaluationCase.path("expectedIntent").asText()));
            }
        }
    }
}
