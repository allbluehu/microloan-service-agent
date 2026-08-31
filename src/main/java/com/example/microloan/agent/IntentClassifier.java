package com.example.microloan.agent;

import java.util.Locale;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class IntentClassifier {

    private static final String PROMPT = """
            你是小额贷客服意图分类器。只输出 NEXT_REPAYMENT_QUERY 或 UNKNOWN。
            NEXT_REPAYMENT_QUERY 表示用户在询问下一期、下个月或近期应还金额/日期。
            用户输入：%s
            """;

    private final ObjectProvider<ChatModel> chatModelProvider;

    public IntentClassifier(ObjectProvider<ChatModel> chatModelProvider) {
        this.chatModelProvider = chatModelProvider;
    }

    public Intent classify(String message) {
        ChatModel model = chatModelProvider.getIfAvailable();
        if (model != null) {
            try {
                String output = model.call(PROMPT.formatted(message)).trim().toUpperCase(Locale.ROOT);
                return output.contains("NEXT_REPAYMENT_QUERY") ? Intent.NEXT_REPAYMENT_QUERY : Intent.UNKNOWN;
            } catch (RuntimeException ignored) {
                // Classification can safely degrade to narrow rules; business facts never come from this fallback.
            }
        }
        return ruleFallback(message);
    }

    private Intent ruleFallback(String message) {
        boolean repaymentTopic = message.contains("还") || message.contains("账单");
        boolean nextPeriod = message.contains("下个月") || message.contains("下期") || message.contains("下一期")
                || message.contains("什么时候") || message.contains("多少");
        return repaymentTopic && nextPeriod ? Intent.NEXT_REPAYMENT_QUERY : Intent.UNKNOWN;
    }
}
