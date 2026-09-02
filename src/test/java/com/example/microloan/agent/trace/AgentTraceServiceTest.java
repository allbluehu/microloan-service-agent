package com.example.microloan.agent.trace;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class AgentTraceServiceTest {

    @Test
    void recordsConfiguredModelOnlyWhenModelResultWasAccepted() {
        AgentTraceMapper mapper = mock(AgentTraceMapper.class);
        AgentTraceService service = new AgentTraceService(mapper, "glm-4.7-flash");
        AgentTraceService.TraceContext modelTrace = service.start("T-MODEL", "S1", 10001L);
        AgentTraceService.TraceContext ruleTrace = service.start("T-RULE", "S2", 10001L);

        service.intentSuccess(modelTrace, "messageId=M1",
                "intent=NEXT_REPAYMENT_QUERY,confidence=0.98,source=MODEL", true, 120);
        service.intentSuccess(ruleTrace, "messageId=M2",
                "intent=NEXT_REPAYMENT_QUERY,confidence=1.0,source=RULE", false, 3);

        verify(mapper).insert(eq("T-MODEL"), eq("S1"), eq(10001L), eq(1),
                eq("INTENT_CLASSIFIER"), eq("messageId=M1"),
                eq("intent=NEXT_REPAYMENT_QUERY,confidence=0.98,source=MODEL"),
                eq("glm-4.7-flash"), eq("intent-v4"), eq(120L), eq("SUCCESS"));
        verify(mapper).insert(eq("T-RULE"), eq("S2"), eq(10001L), eq(1),
                eq("INTENT_CLASSIFIER"), eq("messageId=M2"),
                eq("intent=NEXT_REPAYMENT_QUERY,confidence=1.0,source=RULE"),
                eq(null), eq("intent-v4"), eq(3L), eq("SUCCESS"));
    }
}
