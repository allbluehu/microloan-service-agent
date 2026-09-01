package com.example.microloan.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CompositeIntentDetectorTest {
    private final CompositeIntentDetector detector = new CompositeIntentDetector();

    @Test
    void requiresClarificationForTwoBusinessActions() {
        assertThat(detector.requiresClarification("查一下下一期账单，顺便看看还款记录")).isTrue();
    }

    @Test
    void explicitHumanHandoffKeepsItsPriority() {
        assertThat(detector.requiresClarification("查账单并帮我转人工客服")).isFalse();
    }
}
