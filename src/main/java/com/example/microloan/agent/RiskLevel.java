package com.example.microloan.agent;

public enum RiskLevel {
    /** 未发现需优先接管的高风险信号。 */
    LOW,
    /** 需要停止普通路由并优先创建人工工单。 */
    HIGH
}
