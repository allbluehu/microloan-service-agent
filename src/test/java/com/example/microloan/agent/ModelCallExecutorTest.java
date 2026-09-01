package com.example.microloan.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;

class ModelCallExecutorTest {

    @Test
    void returnsCompletedModelCall() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ModelCallExecutor executor = new ModelCallExecutor(
                Duration.ofSeconds(1),
                new ModelCircuitBreaker(3, Duration.ofSeconds(30), System::nanoTime),
                registry);

        assertThat(executor.call(() -> "ok")).isEqualTo("ok");
        assertThat(registry.get("microloan.agent.model.calls")
                .tag("outcome", "success").counter().count()).isEqualTo(1);
        assertThat(registry.get("microloan.agent.model.call.duration").timer().count()).isEqualTo(1);
        executor.close();
    }

    @Test
    void cancelsModelCallAfterConfiguredTimeout() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ModelCallExecutor executor = new ModelCallExecutor(
                Duration.ofMillis(20),
                new ModelCircuitBreaker(3, Duration.ofSeconds(30), System::nanoTime),
                registry);

        assertThatThrownBy(() -> executor.call(() -> {
            LockSupport.parkNanos(Duration.ofSeconds(1).toNanos());
            return "late";
        })).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("configured timeout");
        assertThat(registry.get("microloan.agent.model.calls")
                .tag("outcome", "timeout").counter().count()).isEqualTo(1);
        executor.close();
    }

    @Test
    void recordsExecutionFailureAndCircuitRejectionSeparately() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicLong ticker = new AtomicLong();
        ModelCircuitBreaker circuitBreaker = new ModelCircuitBreaker(
                1, Duration.ofSeconds(30), ticker::get);
        ModelCallExecutor executor = new ModelCallExecutor(
                Duration.ofSeconds(1), circuitBreaker, registry);

        assertThatThrownBy(() -> executor.call(() -> {
            throw new IllegalStateException("model unavailable");
        })).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> executor.call(() -> "ignored"))
                .hasMessageContaining("circuit breaker is open");

        assertThat(registry.get("microloan.agent.model.calls")
                .tag("outcome", "failure").counter().count()).isEqualTo(1);
        assertThat(registry.get("microloan.agent.model.calls")
                .tag("outcome", "circuit_open").counter().count()).isEqualTo(1);
        assertThat(registry.get("microloan.agent.model.call.duration").timer().count()).isEqualTo(2);
        executor.close();
    }
}
