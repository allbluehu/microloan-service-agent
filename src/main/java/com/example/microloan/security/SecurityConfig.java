package com.example.microloan.security;

import com.example.microloan.api.ApiError;
import com.example.microloan.api.RequestTraceContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                            KnowledgeAdminAuthenticationFilter knowledgeAdminFilter,
                                            ObjectMapper objectMapper) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 登录入口、人工测试页及容器探针需要在没有客户 JWT 时可访问；业务 API 仍统一鉴权。
                        .requestMatchers("/api/v1/auth/login", "/chat.html", "/knowledge-admin.html",
                                "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/admin/knowledge/**").hasRole("KNOWLEDGE_ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                    // Security rejects requests before MVC, so it must write the shared API error contract itself.
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    objectMapper.writeValue(response.getOutputStream(), new ApiError(
                            "AUTH_REQUIRED", "需要登录后访问", RequestTraceContext.from(request)));
                }).accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    objectMapper.writeValue(response.getOutputStream(), new ApiError(
                            "ACCESS_DENIED", "没有访问该资源的权限", RequestTraceContext.from(request)));
                }))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(knowledgeAdminFilter, JwtAuthenticationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
