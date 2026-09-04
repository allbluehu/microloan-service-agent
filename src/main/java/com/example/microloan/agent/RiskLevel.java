package com.example.microloan.agent;

public enum RiskLevel {
    /** 未发现需优先接管的高风险信号。 */
    LOW,
    /** 存在弱风险信号，需要模型复核；最终编排结果不会保留该中间状态。 */
    REVIEW,
    /** 需要停止普通路由并优先创建人工工单。 */
    HIGH
}
