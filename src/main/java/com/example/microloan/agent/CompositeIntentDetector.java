package com.example.microloan.agent;

import org.springframework.stereotype.Component;

@Component
public class CompositeIntentDetector {

    public boolean requiresClarification(String content) {
        if (containsAny(content, "转人工", "人工客服", "找人工")) {
            // 明确人工请求具有确定优先级，不需要在自动化业务动作之间做选择。
            return false;
        }
        int matched = 0;
        matched += isNextRepayment(content) ? 1 : 0;
        matched += isApplicationStatus(content) ? 1 : 0;
        matched += containsAny(content, "还款记录", "支付记录", "还款历史", "扣款记录") ? 1 : 0;
        matched += containsAny(content, "提前结清", "提前还清") ? 1 : 0;
        matched += containsAny(content, "申请条件", "需要什么材料", "准备什么", "多少期", "期限", "利率", "费用", "办理流程") ? 1 : 0;
        return matched > 1;
    }

    private boolean isNextRepayment(String content) {
        return containsAny(content, "还", "账单")
                && containsAny(content, "下个月", "下期", "下一期", "什么时候", "多少");
    }

    private boolean isApplicationStatus(String content) {
        return containsAny(content, "申请", "审批")
                && containsAny(content, "进度", "状态", "到哪", "材料");
    }

    private boolean containsAny(String content, String... terms) {
        for (String term : terms) {
            if (content.contains(term)) {
                return true;
            }
        }
        return false;
    }
}
