package com.example.microloan.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 复合意图前置决策测试。
 * 该检测器位于模型分类之前，负责阻止一条消息触发多个普通业务动作；明确转人工则按既定优先级直接路由，
 * 不应被普通业务词项误判为需要澄清。
 */
class CompositeIntentDetectorTest {
    private final CompositeIntentDetector detector = new CompositeIntentDetector();

    @Test
    void requiresClarificationForTwoBusinessActions() {
        // 账单与还款记录是两个独立工具分支，必须先让客户选择，不能猜测执行顺序。
        assertThat(detector.requiresClarification("查一下下一期账单，顺便看看还款记录")).isTrue();
    }

    @Test
    void explicitHumanHandoffKeepsItsPriority() {
        // 人工诉求是确定性终点，即使文本同时包含业务问题，也无需模型或客户再次消歧。
        assertThat(detector.requiresClarification("查账单并帮我转人工客服")).isFalse();
    }
}
