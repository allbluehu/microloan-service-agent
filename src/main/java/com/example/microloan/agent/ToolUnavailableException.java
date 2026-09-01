package com.example.microloan.agent;

public class ToolUnavailableException extends RuntimeException {
    public ToolUnavailableException(Throwable cause) {
        super("业务服务暂时不可用，请稍后重试或转人工", cause);
    }
}
