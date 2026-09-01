package com.example.microloan.security;

public enum AuthLevel {
    /** 普通登录认证，可执行低敏查询。 */
    LOGIN,
    /** 短时二次认证，可执行提前结清等敏感操作。 */
    STEP_UP;

    public boolean satisfies(AuthLevel required) {
        // 枚举顺序表达认证强度，只允许更强认证满足较弱要求。
        return ordinal() >= required.ordinal();
    }
}
