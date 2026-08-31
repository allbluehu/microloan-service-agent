package com.example.microloan.api;

public record ApiError(String code, String message, String traceId) {}
