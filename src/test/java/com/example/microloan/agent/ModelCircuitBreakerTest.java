package com.example.microloan.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ModelCircuitBreakerTest {

    @Test
    void opensAfterConsecutiveFailures() {
        ModelCircuitBreaker breaker = new ModelCircuitBreaker(2, Duration.ofSeconds(30), () -> 0L);

        assertThat(breaker.tryAcquirePermission()).isTrue();
        breaker.recordFailure();
        assertThat(breaker.tryAcquirePermission()).isTrue();
        breaker.recordFailure();

        assertThat(breaker.tryAcquirePermission()).isFalse();
    }

    @Test
    void allowsOneProbeAfterCoolingDownAndClosesOnSuccess() {
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
