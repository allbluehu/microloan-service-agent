package com.example.microloan.security;

public enum AuthLevel {
    LOGIN,
    STEP_UP;

    public boolean satisfies(AuthLevel required) {
        return ordinal() >= required.ordinal();
    }
}
