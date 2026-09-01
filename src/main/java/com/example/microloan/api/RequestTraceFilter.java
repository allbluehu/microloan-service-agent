package com.example.microloan.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 服务端生成标识，不接受外部值，避免恶意内容进入响应头和日志上下文。
        String traceId = RequestTraceContext.create(request);
        response.setHeader(RequestTraceContext.RESPONSE_HEADER, traceId);
        MDC.put("traceId", traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Servlet 工作线程会被复用，必须清理 MDC，防止后续请求串用本轮标识。
            MDC.remove("traceId");
        }
    }
}
