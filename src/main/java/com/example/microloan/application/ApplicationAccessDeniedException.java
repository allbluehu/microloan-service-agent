package com.example.microloan.application;

public class ApplicationAccessDeniedException extends RuntimeException {
    public ApplicationAccessDeniedException() {
        super("无法访问该申请信息");
    }
}
