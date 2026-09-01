package com.example.microloan.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class RequestTraceContext {
    public static final String ATTRIBUTE = RequestTraceContext.class.getName() + ".traceId";
    public static final String RESPONSE_HEADER = "X-Trace-Id";

    private RequestTraceContext() {}

    public static String create(HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, traceId);
        return traceId;
    }

    public static String from(HttpServletRequest request) {
        Object traceId = request.getAttribute(ATTRIBUTE);
        return traceId instanceof String value ? value : create(request);
    }

    public static String currentOrCreate() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return from(servletAttributes.getRequest());
        }
        // 非 HTTP 调用（例如单元测试或后台任务）仍生成可审计标识，不依赖线程请求上下文。
        return UUID.randomUUID().toString();
    }
}
