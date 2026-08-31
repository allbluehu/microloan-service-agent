package com.example.microloan.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;
    private final Duration stepUpTtl;

    public JwtService(@Value("${security.jwt.secret}") String secret,
                      @Value("${security.jwt.ttl}") Duration ttl,
                      @Value("${security.jwt.step-up-ttl}") Duration stepUpTtl) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = ttl;
        this.stepUpTtl = stepUpTtl;
    }

    public String issue(Long customerId, String customerNo) {
        return issue(customerId, customerNo, AuthLevel.LOGIN, ttl);
    }

    public String issueStepUp(Long customerId, String customerNo) {
        return issue(customerId, customerNo, AuthLevel.STEP_UP, stepUpTtl);
    }

    private String issue(Long customerId, String customerNo, AuthLevel authLevel, Duration tokenTtl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(customerNo)
                .claim("customerId", customerId)
                .claim("authLevel", authLevel.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(tokenTtl)))
                .signWith(key)
                .compact();
    }

    public CustomerPrincipal parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return new CustomerPrincipal(claims.get("customerId", Long.class), claims.getSubject(),
                AuthLevel.valueOf(claims.get("authLevel", String.class)));
    }
}
