package com.example.microloan.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class IntentClassifier {

    public static final String PROMPT_VERSION = "intent-v1";
    private static final String PROMPT_RESOURCE = "prompts/intent-classifier-v1.txt";
    private static final String PROMPT = loadPrompt();

    private final ObjectProvider<ChatModel> chatModelProvider;

    public IntentClassifier(ObjectProvider<ChatModel> chatModelProvider) {
        this.chatModelProvider = chatModelProvider;
    }

    public Intent classify(String message) {
        ChatModel model = chatModelProvider.getIfAvailable();
        if (model != null) {
            try {
                String output = model.call(renderPrompt(message)).trim().toUpperCase(Locale.ROOT);
                // Match the more specific known labels instead of accepting arbitrary model prose as routing data.
                if (output.contains("APPLICATION_STATUS_QUERY")) {
                    return Intent.APPLICATION_STATUS_QUERY;
                }
                if (output.contains("PAYMENT_RECORDS_QUERY")) {
                    return Intent.PAYMENT_RECORDS_QUERY;
                }
                if (output.contains("EARLY_SETTLEMENT_QUOTE")) {
                    return Intent.EARLY_SETTLEMENT_QUOTE;
                }
                if (output.contains("HUMAN_HANDOFF")) {
                    return Intent.HUMAN_HANDOFF;
                }
                if (output.contains("KNOWLEDGE_QUERY")) {
                    return Intent.KNOWLEDGE_QUERY;
                }
                return output.contains("NEXT_REPAYMENT_QUERY") ? Intent.NEXT_REPAYMENT_QUERY : Intent.UNKNOWN;
            } catch (RuntimeException ignored) {
                // Classification can safely degrade to narrow rules; business facts never come from this fallback.
            }
        }
        return ruleFallback(message);
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
}
