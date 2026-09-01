package com.example.microloan.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;

class ModelCallExecutorTest {

    @Test
    void returnsCompletedModelCall() {
        ModelCallExecutor executor = new ModelCallExecutor(Duration.ofSeconds(1));

        assertThat(executor.call(() -> "ok")).isEqualTo("ok");
        executor.close();
    }

    @Test
    void cancelsModelCallAfterConfiguredTimeout() {
        ModelCallExecutor executor = new ModelCallExecutor(Duration.ofMillis(20));

        assertThatThrownBy(() -> executor.call(() -> {
            LockSupport.parkNanos(Duration.ofSeconds(1).toNanos());
            return "late";
        })).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("configured timeout");
        executor.close();
    }
}
