package com.example.microloan.agent;

/** 最终风险判断的来源。 */
public enum RiskSource {
    /** 明确风险由确定性规则识别。 */
    RULE,
    /** 隐晦风险由模型协议结果识别。 */
    MODEL,
    /** 弱风险在模型不可用或不可信时安全升级。 */
    SAFE_FALLBACK,
    /** 未启用模型且规则未发现风险。 */
    DEFAULT
}
