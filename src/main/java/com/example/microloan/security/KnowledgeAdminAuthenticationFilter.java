package com.example.microloan.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class KnowledgeAdminAuthenticationFilter extends OncePerRequestFilter {

    public static final String ADMIN_KEY_HEADER = "X-Knowledge-Admin-Key";
    private static final String ADMIN_PATH_PREFIX = "/api/v1/admin/knowledge/";

    private final byte[] configuredKey;

    public KnowledgeAdminAuthenticationFilter(
            @Value("${knowledge.admin.api-key:}") String configuredKey) {
        if (!configuredKey.isBlank() && configuredKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("KNOWLEDGE_ADMIN_API_KEY 必须至少为 32 字节");
        }
        this.configuredKey = configuredKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(ADMIN_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String suppliedKey = request.getHeader(ADMIN_KEY_HEADER);
        if (configuredKey.length > 0 && suppliedKey != null
                && MessageDigest.isEqual(configuredKey, suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            // 管理密钥只授予知识治理权限，不复用客户身份，也不能访问客户私有业务 API。
            var authentication = new UsernamePasswordAuthenticationToken(
                    "knowledge-admin", null, List.of(new SimpleGrantedAuthority("ROLE_KNOWLEDGE_ADMIN")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter(request, response);
    }
}
