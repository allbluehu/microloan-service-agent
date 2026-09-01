package com.example.microloan.api;

public record ApiError(
        /** 稳定的机器可读错误码。 */
        String code,
        /** 面向调用方的错误说明。 */
        String message,
        /** 用于关联日志与调用链的追踪标识。 */
        String traceId
) {}
