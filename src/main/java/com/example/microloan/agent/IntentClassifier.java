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
/**
 * 将客户文本收敛为协议内意图，并保证模型不可用或输出不可信时仍有确定性结果。
 *
 * <p>调用链为 AgentOrchestrator → {@link #classifyDetailed} → ChatModel（可选）→
 * {@link ModelCallExecutor}。只有严格 JSON、合法枚举和不低于阈值的输出标记为 MODEL；其余所有情况均
 * 回退到范围较窄的本地规则。高风险识别不在此处处理，而由编排器前置调用 RiskClassifier。</p>
 */
public class IntentClassifier {

    public static final String PROMPT_VERSION = "intent-v4";
    private static final double MODEL_CONFIDENCE_THRESHOLD = 0.75;
    private static final int MAX_MODEL_INPUT_LENGTH = 1000;
    private static final String PROMPT_RESOURCE = "prompts/intent-classifier-v4.txt";
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

    /**
     * 返回意图、置信度及来源，供路由和 Trace 同时使用。
     * 模型异常会在此边界被吸收，因为意图分类失败可以安全降级且不提供任何业务事实。
     */
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
            // JSON 字面量 null 可被反序列化，但不构成合法分类结果。
            if (parsed == null || parsed.intent() == null) {
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
        // 外部模型只接收完成意图判断所需的有限文本，持久化与模型发送共用同一脱敏规则。
        String safeMessage = SensitiveTextSanitizer.redactAndLimit(message, MAX_MODEL_INPUT_LENGTH);
        try {
            // JSON 编码会转义引号、换行等结构字符，客户内容无法伪造提示词的数据边界。
            return PROMPT.formatted(objectMapper.writeValueAsString(safeMessage));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to encode model input", exception);
        }
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
        boolean generalApplicationMaterials = message.contains("申请材料")
                && !(message.contains("缺") || message.contains("补") || message.contains("提交")
                || message.contains("进度") || message.contains("状态"));
        boolean knowledgeTopic = message.contains("申请条件") || message.contains("需要什么材料")
                || message.contains("准备什么") || message.contains("多少期") || message.contains("期限")
                || message.contains("利率") || message.contains("费用") || message.contains("办理流程")
                || message.contains("申请流程") || generalApplicationMaterials;
        if (knowledgeTopic) {
            return Intent.KNOWLEDGE_QUERY;
        }
        boolean historicalPayment = message.contains("还款记录") || message.contains("支付记录")
                || message.contains("还款历史") || message.contains("历史还款")
                || message.contains("还款成功") || message.contains("扣款记录");
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
