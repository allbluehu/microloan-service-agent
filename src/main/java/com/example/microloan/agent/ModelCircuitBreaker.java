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

    public synchronized void recordSuccess() {
        state = State.CLOSED;
        consecutiveFailures = 0;
    }

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
