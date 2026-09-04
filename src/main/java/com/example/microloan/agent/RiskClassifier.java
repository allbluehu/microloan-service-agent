package com.example.microloan.agent;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/** 组合规则、可选模型与安全决策策略的风险识别门面。 */
@Component
public class RiskClassifier {

    private final RuleRiskClassifier ruleClassifier;
    private final ModelRiskClassifier modelClassifier;
    private final RiskDecisionPolicy decisionPolicy;

    @Autowired
    public RiskClassifier(RuleRiskClassifier ruleClassifier, ModelRiskClassifier modelClassifier,
                          RiskDecisionPolicy decisionPolicy) {
        this.ruleClassifier = ruleClassifier;
        this.modelClassifier = modelClassifier;
        this.decisionPolicy = decisionPolicy;
    }

    RiskClassifier(RuleRiskClassifier ruleClassifier, RiskDecisionPolicy decisionPolicy) {
        this(ruleClassifier, null, decisionPolicy);
    }

    public RiskAssessment classify(String content) {
        RiskAssessment rule = ruleClassifier.classify(content);
        // 明确规则命中后不再调用外部模型，降低延迟且不允许模型覆盖确定性高风险结果。
        return rule.level() == RiskLevel.HIGH
                ? rule
                : decisionPolicy.decide(rule,
                        modelClassifier == null ? java.util.Optional.empty() : modelClassifier.classify(content));
    }
}
