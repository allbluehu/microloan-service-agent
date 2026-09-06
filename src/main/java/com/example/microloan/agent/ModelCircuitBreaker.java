package com.example.microloan.agent;

import java.time.Duration;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ModelCircuitBreaker {
    private final int failureThreshold;
    private final long openNanos;
    private final LongSupplier ticker;
    private State state = State.CLOSED;
    private int consecutiveFailures;
    private long retryAtNanos;

    @Autowired
    public ModelCircuitBreaker(@Value("${agent.model-call.circuit-breaker.failure-threshold:3}") int failureThreshold,
                               @Value("${agent.model-call.circuit-breaker.open-duration:30s}") Duration openDuration) {
        this(failureThreshold, openDuration, System::nanoTime);
    }

    ModelCircuitBreaker(int failureThreshold, Duration openDuration, LongSupplier ticker) {
        if (failureThreshold < 1 || openDuration.isNegative() || openDuration.isZero()) {
            throw new IllegalArgumentException("Circuit breaker threshold and duration must be positive");
        }
        this.failureThreshold = failureThreshold;
        this.openNanos = openDuration.toNanos();
        this.ticker = ticker;
    }

    /** 串行判断并更新状态，保证冷却结束时只有一个请求取得半开探测资格。 */
    public synchronized boolean tryAcquirePermission() {
        if (state == State.CLOSED) {
            return true;
        }
        if (state == State.OPEN && ticker.getAsLong() >= retryAtNanos) {
            // 冷却结束后只放行一个探测请求，其余并发请求继续快速降级。
            state = State.HALF_OPEN;
            return true;
        }
        return false;
    }

    /** 调用正常返回后关闭熔断并清零连续失败数；不代表外层模型协议校验通过。 */
    public synchronized void recordSuccess() {
        state = State.CLOSED;
        consecutiveFailures = 0;
    }

    /** 半开探测失败立即重新冷却；其他失败达到阈值后熔断，使用单调时钟避免系统时间调整干扰。 */
    public synchronized void recordFailure() {
        consecutiveFailures++;
        if (state == State.HALF_OPEN || consecutiveFailures >= failureThreshold) {
            state = State.OPEN;
            retryAtNanos = ticker.getAsLong() + openNanos;
        }
    }

    enum State {
        /** 正常放行模型调用。 */
        CLOSED,
        /** 快速拒绝模型调用并使用本地降级。 */
        OPEN,
        /** 冷却后仅允许一个恢复探测。 */
        HALF_OPEN
    }
}
