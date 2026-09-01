package com.example.microloan.agent;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ModelCallExecutor {
    private final Duration timeout;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ModelCallExecutor(@Value("${agent.model-call.timeout:3s}") Duration timeout) {
        this.timeout = timeout;
    }

    public String call(Supplier<String> invocation) {
        Future<String> future = executor.submit(invocation::get);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            // 中断超时任务，避免迟到的模型结果在请求结束后继续占用连接和计算资源。
            future.cancel(true);
            throw new ModelCallTimeoutException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ModelCallTimeoutException(exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Model invocation failed", exception.getCause());
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
}
