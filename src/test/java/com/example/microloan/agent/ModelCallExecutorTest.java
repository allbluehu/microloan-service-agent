package com.example.microloan.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;

/**
 * 模型调用隔离层测试。
 * ModelCallExecutor 包围真正的 ChatModel 调用，按“并发许可 → 熔断许可 → 虚拟线程执行 → 总超时”顺序控制资源，
 * 并将固定 outcome 写入 Micrometer；这些测试不访问网络，只验证并发与状态转换。
 */
class ModelCallExecutorTest {

    @Test
    void returnsCompletedModelCall() {
        // 正常完成必须同时返回模型结果、记录 success 次数并写入统一耗时指标。
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ModelCallExecutor executor = new ModelCallExecutor(
                Duration.ofSeconds(1), 20,
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
        // park 模拟不响应的供应商；总预算到期后任务应被中断，调用方随后可进入规则降级。
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ModelCallExecutor executor = new ModelCallExecutor(
                Duration.ofMillis(20), 20,
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
        // 阈值设为 1 让首次失败立即开路，第二次调用不得再执行 Supplier。
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicLong ticker = new AtomicLong();
        ModelCircuitBreaker circuitBreaker = new ModelCircuitBreaker(
                1, Duration.ofSeconds(30), ticker::get);
        ModelCallExecutor executor = new ModelCallExecutor(
                Duration.ofSeconds(1), 20, circuitBreaker, registry);

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

    @Test
    void rejectsImmediatelyWhenConcurrentCapacityIsExhausted() throws Exception {
        // 两个闩锁稳定占住唯一许可，证明第二个调用不是等待超时，而是立即按容量不足拒绝。
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ModelCallExecutor executor = new ModelCallExecutor(
                Duration.ofSeconds(1), 1,
                new ModelCircuitBreaker(3, Duration.ofSeconds(30), System::nanoTime),
                registry);
        CountDownLatch invocationStarted = new CountDownLatch(1);
        CountDownLatch releaseInvocation = new CountDownLatch(1);
        CompletableFuture<String> firstCall = CompletableFuture.supplyAsync(() -> executor.call(() -> {
            invocationStarted.countDown();
            try {
                releaseInvocation.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return "first";
        }));
        assertThat(invocationStarted.await(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> executor.call(() -> "second"))
                .hasMessageContaining("capacity is exhausted");
        assertThat(registry.get("microloan.agent.model.calls")
                .tag("outcome", "capacity_rejected").counter().count()).isEqualTo(1);

        releaseInvocation.countDown();
        assertThat(firstCall.join()).isEqualTo("first");
        executor.close();
    }
}
