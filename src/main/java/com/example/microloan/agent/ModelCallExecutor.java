package com.example.microloan.agent;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
/**
 * 真实模型调用的统一可靠性边界。
 *
 * <p>调用方提交同步 Supplier，本类按“并发许可 → 熔断许可 → 虚拟线程执行 → 总超时”顺序控制调用，
 * 并记录低基数指标。异常返回给 IntentClassifier，由分类器决定规则降级；本类不理解意图和业务数据。</p>
 */
public class ModelCallExecutor {
    private final Duration timeout;
    private final ModelCircuitBreaker circuitBreaker;
    private final MeterRegistry meterRegistry;
    private final Semaphore capacity;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Autowired
    public ModelCallExecutor(@Value("${agent.model-call.timeout:3s}") Duration timeout,
                             @Value("${agent.model-call.max-concurrent:20}") int maxConcurrent,
                             ModelCircuitBreaker circuitBreaker,
                             MeterRegistry meterRegistry) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("Model max concurrency must be positive");
        }
        this.timeout = timeout;
        this.circuitBreaker = circuitBreaker;
        this.meterRegistry = meterRegistry;
        this.capacity = new Semaphore(maxConcurrent);
    }

    ModelCallExecutor(Duration timeout) {
        this(timeout, 20,
                new ModelCircuitBreaker(3, Duration.ofSeconds(30), System::nanoTime),
                new SimpleMeterRegistry());
    }

    /**
     * 在配置的总预算内执行一次模型调用。
     *
     * @throws RuntimeException 容量耗尽、熔断、超时或供应商调用失败；调用方不得将异常正文返回客户
     */
    public String call(Supplier<String> invocation) {
        long startedAt = System.nanoTime();
        String outcome = "failure";
        boolean capacityAcquired = false;
        try {
            // 容量耗尽时立即降级，避免请求在线程或模型连接前无限排队并放大尾延迟。
            if (!capacity.tryAcquire()) {
                outcome = "capacity_rejected";
                throw new ModelCapacityExceededException();
            }
            capacityAcquired = true;
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
            if (capacityAcquired) {
                capacity.release();
            }
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

    private static final class ModelCapacityExceededException extends RuntimeException {
        private ModelCapacityExceededException() {
            super("Model call capacity is exhausted");
        }
    }
}
