package com.example.microloan.customer;

public record Customer(
        /** 客户数据库主键。 */
        Long id,
        /** 对外使用的客户编号。 */
        String customerNo,
        /** 客户展示名称。 */
        String name,
        /** BCrypt 密码摘要，不得通过接口返回。 */
        String passwordHash,
        /** 客户账户状态。 */
        String status
) {}
