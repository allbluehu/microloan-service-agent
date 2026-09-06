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
 * 并记录低基数指标。异常交回意图或风险分类器，由各自策略决定降级；本类不解释模型协议和业务数据。</p>
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
     * 在配置的总预算内执行一次模型调用，包括 Supplier 内部的提示词构造及模型客户端有限重试。
     * 风险与意图分类复用该组件，因此共享实例级并发容量和熔断状态。
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
                // 此处成功仅指 Supplier 正常返回；非法协议或低置信度由外层分类器处理，不计入调用熔断失败。
                circuitBreaker.recordSuccess();
                outcome = "success";
                return result;
            } catch (TimeoutException exception) {
                // 尝试中断超时任务并丢弃迟到结果；底层客户端是否立即停止取决于其对线程中断的支持。
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
