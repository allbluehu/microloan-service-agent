package com.example.microloan.agent;

public enum FeedbackReason {
    /** 信息已经过期。 */
    OUTDATED_INFORMATION,
    /** 回答包含错误信息。 */
    INCORRECT_ANSWER,
    /** 回答正确但没有解决问题。 */
    NOT_HELPFUL,
    /** 其他受控原因。 */
    OTHER
}
