package com.example.microloan.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class IntentClassifier {

    public static final String PROMPT_VERSION = "intent-v2";
    private static final double MODEL_CONFIDENCE_THRESHOLD = 0.75;
    private static final String PROMPT_RESOURCE = "prompts/intent-classifier-v2.txt";
    private static final String PROMPT = loadPrompt();

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectMapper objectMapper;
    private final ModelCallExecutor modelCallExecutor;

    public IntentClassifier(ObjectProvider<ChatModel> chatModelProvider, ObjectMapper objectMapper,
                            ModelCallExecutor modelCallExecutor) {
        this.chatModelProvider = chatModelProvider;
        this.objectMapper = objectMapper;
        this.modelCallExecutor = modelCallExecutor;
    }

    public Intent classify(String message) {
        return classifyDetailed(message).intent();
    }

    public ClassificationResult classifyDetailed(String message) {
        ChatModel model = chatModelProvider.getIfAvailable();
        if (model != null) {
            try {
                ClassificationResult parsed = parseModelOutput(
                        modelCallExecutor.call(() -> model.call(renderPrompt(message))));
                if (parsed != null && parsed.confidence() >= MODEL_CONFIDENCE_THRESHOLD) {
                    return parsed;
                }
            } catch (RuntimeException ignored) {
                // Classification can safely degrade to narrow rules; business facts never come from this fallback.
            }
        }
        Intent fallback = ruleFallback(message);
        return new ClassificationResult(fallback, fallback == Intent.UNKNOWN ? 0.0 : 1.0, ClassificationSource.RULE);
    }

    ClassificationResult parseModelOutput(String output) {
        if (output == null) {
            return null;
        }
        try {
            ModelOutput parsed = objectMapper.readValue(output, ModelOutput.class);
            if (parsed.intent() == null) {
                return null;
            }
            Intent intent = Intent.valueOf(parsed.intent());
            if (intent == Intent.HIGH_RISK_COMPLAINT || parsed.confidence() == null
                    || parsed.confidence() < 0 || parsed.confidence() > 1) {
                return null;
            }
            return new ClassificationResult(intent, parsed.confidence(), ClassificationSource.MODEL);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            // JSON 结构、标签或置信度任一越界都视为不可信模型输出。
            return null;
        }
    }

    String renderPrompt(String message) {
        return PROMPT.formatted(message);
    }

    private static String loadPrompt() {
        try {
            return new ClassPathResource(PROMPT_RESOURCE).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            // 提示词属于受版本控制的必备运行资源，缺失时应在启动阶段暴露部署错误。
            throw new IllegalStateException("Missing intent prompt resource: " + PROMPT_RESOURCE, exception);
        }
    }

    private Intent ruleFallback(String message) {
        if (message.contains("转人工") || message.contains("人工客服") || message.contains("找人工")) {
            return Intent.HUMAN_HANDOFF;
        }
        if (message.contains("提前结清") || message.contains("提前还清")) {
            return Intent.EARLY_SETTLEMENT_QUOTE;
        }
        boolean knowledgeTopic = message.contains("申请条件") || message.contains("需要什么材料")
                || message.contains("准备什么") || message.contains("多少期") || message.contains("期限")
                || message.contains("利率") || message.contains("费用") || message.contains("办理流程");
        if (knowledgeTopic) {
            return Intent.KNOWLEDGE_QUERY;
        }
        boolean historicalPayment = message.contains("还款记录") || message.contains("支付记录")
                || message.contains("还款历史") || message.contains("还款成功") || message.contains("扣款记录");
        if (historicalPayment) {
            return Intent.PAYMENT_RECORDS_QUERY;
        }
        boolean applicationTopic = message.contains("申请") || message.contains("审批");
        boolean applicationStatus = message.contains("进度") || message.contains("状态")
                || message.contains("到哪") || message.contains("材料");
        if (applicationTopic && applicationStatus) {
            return Intent.APPLICATION_STATUS_QUERY;
        }
        boolean repaymentTopic = message.contains("还") || message.contains("账单");
        boolean nextPeriod = message.contains("下个月") || message.contains("下期") || message.contains("下一期")
                || message.contains("什么时候") || message.contains("多少");
        return repaymentTopic && nextPeriod ? Intent.NEXT_REPAYMENT_QUERY : Intent.UNKNOWN;
    }

    public record ClassificationResult(
            /** 最终用于确定性路由的意图。 */
            Intent intent,
            /** 分类置信度，取值范围为 0 到 1。 */
            double confidence,
            /** 分类结果来自模型还是本地规则。 */
            ClassificationSource source
    ) {}

    public enum ClassificationSource {
        /** 通过结构化模型输出得到。 */
        MODEL,
        /** 由本地确定性规则生成或降级得到。 */
        RULE
    }

    private record ModelOutput(
            /** 模型返回的意图标签。 */
            String intent,
            /** 模型返回的置信度。 */
            Double confidence
    ) {}
}
