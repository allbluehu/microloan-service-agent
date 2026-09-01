package com.example.microloan.security;

public record CustomerPrincipal(
        /** 当前已认证客户的数据库主键。 */
        Long customerId,
        /** 当前已认证客户的对外编号。 */
        String customerNo,
        /** 当前令牌达到的认证强度。 */
        AuthLevel authLevel
) {}
