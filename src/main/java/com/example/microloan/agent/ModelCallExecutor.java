package com.example.microloan.agent;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ModelCallExecutor {
    private final Duration timeout;
    private final ModelCircuitBreaker circuitBreaker;
    private final MeterRegistry meterRegistry;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Autowired
    public ModelCallExecutor(@Value("${agent.model-call.timeout:3s}") Duration timeout,
                             ModelCircuitBreaker circuitBreaker,
                             MeterRegistry meterRegistry) {
        this.timeout = timeout;
        this.circuitBreaker = circuitBreaker;
        this.meterRegistry = meterRegistry;
    }

    ModelCallExecutor(Duration timeout) {
        this(timeout,
                new ModelCircuitBreaker(3, Duration.ofSeconds(30), System::nanoTime),
                new SimpleMeterRegistry());
    }

    public String call(Supplier<String> invocation) {
        long startedAt = System.nanoTime();
        String outcome = "failure";
        try {
            if (!circuitBreaker.tryAcquirePermission()) {
                outcome = "circuit_open";
                throw new ModelCircuitOpenException();
            }
            Future<String> future = executor.submit(invocation::get);
            try {
                String result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                circuitBreaker.recordSuccess();
                outcome = "success";
                return result;
            } catch (TimeoutException exception) {
                // 中断超时任务，避免迟到的模型结果在请求结束后继续占用连接和计算资源。
                future.cancel(true);
                circuitBreaker.recordFailure();
                outcome = "timeout";
                throw new ModelCallTimeoutException(exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                circuitBreaker.recordFailure();
                outcome = "interrupted";
                throw new ModelCallTimeoutException(exception);
            } catch (ExecutionException exception) {
                circuitBreaker.recordFailure();
                if (exception.getCause() instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException("Model invocation failed", exception.getCause());
            }
        } finally {
            // 指标仅使用固定结果标签，避免客户或提示词进入监控系统造成泄露和基数膨胀。
            meterRegistry.counter("microloan.agent.model.calls", "outcome", outcome).increment();
            meterRegistry.timer("microloan.agent.model.call.duration")
                    .record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
        }
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
    }

    private static final class ModelCallTimeoutException extends RuntimeException {
        private ModelCallTimeoutException(Exception cause) {
            super("Model call exceeded configured timeout", cause);
        }
    }

    private static final class ModelCircuitOpenException extends RuntimeException {
        private ModelCircuitOpenException() {
            super("Model circuit breaker is open");
        }
    }
}
