package com.example.microloan.agent;

import java.util.Locale;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class IntentClassifier {

    private static final String PROMPT = """
            你是小额贷客服意图分类器。只输出以下一个标签：
            NEXT_REPAYMENT_QUERY、APPLICATION_STATUS_QUERY、PAYMENT_RECORDS_QUERY、EARLY_SETTLEMENT_QUOTE、
            HUMAN_HANDOFF、KNOWLEDGE_QUERY、UNKNOWN。
            NEXT_REPAYMENT_QUERY 表示用户在询问下一期、下个月或近期应还金额/日期。
            APPLICATION_STATUS_QUERY 表示用户在询问贷款申请的进度、状态或缺失资料。
            PAYMENT_RECORDS_QUERY 表示用户在询问过去的还款记录、支付记录或某次还款是否成功。
            EARLY_SETTLEMENT_QUOTE 表示用户希望试算提前还清或提前结清贷款的金额。
            HUMAN_HANDOFF 表示用户明确要求人工客服或转人工。
            KNOWLEDGE_QUERY 表示用户咨询产品条件、材料、期限、利率、费用或办理流程。
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
