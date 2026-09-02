package com.example.microloan.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * 模型熔断器状态机测试。
 * 使用可控纳秒时钟避免真实等待，验证 CLOSED → OPEN → HALF_OPEN → CLOSED 的核心转换以及半开单探测约束。
 */
class ModelCircuitBreakerTest {

    @Test
    void opensAfterConsecutiveFailures() {
        // 达到阈值前仍允许请求，第二次连续失败后立即拒绝后续模型调用。
        ModelCircuitBreaker breaker = new ModelCircuitBreaker(2, Duration.ofSeconds(30), () -> 0L);

        assertThat(breaker.tryAcquirePermission()).isTrue();
        breaker.recordFailure();
        assertThat(breaker.tryAcquirePermission()).isTrue();
        breaker.recordFailure();

        assertThat(breaker.tryAcquirePermission()).isFalse();
    }

    @Test
    void allowsOneProbeAfterCoolingDownAndClosesOnSuccess() {
        // 冷却期结束只放行一个探测请求，避免供应商刚恢复时被并发流量再次压垮。
        AtomicLong ticker = new AtomicLong();
        ModelCircuitBreaker breaker = new ModelCircuitBreaker(1, Duration.ofSeconds(1), ticker::get);
        breaker.recordFailure();
        ticker.set(Duration.ofSeconds(1).toNanos());

        assertThat(breaker.tryAcquirePermission()).isTrue();
        assertThat(breaker.tryAcquirePermission()).isFalse();
        breaker.recordSuccess();

        assertThat(breaker.tryAcquirePermission()).isTrue();
    }
}
