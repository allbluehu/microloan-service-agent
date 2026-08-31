package com.example.microloan.settlement;

public class StepUpAuthRequiredException extends RuntimeException {
    public StepUpAuthRequiredException() {
        super("提前结清试算需要完成二次认证");
    }
}
