package com.example.microloan.agent.trace;

import com.example.microloan.security.CustomerPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agent/traces")
public class AgentTraceController {

    private final AgentTraceService traceService;

    public AgentTraceController(AgentTraceService traceService) {
        this.traceService = traceService;
    }

    @GetMapping("/{traceId}")
    public List<AgentTraceEntry> getTrace(@AuthenticationPrincipal CustomerPrincipal principal,
                                         @PathVariable String traceId) {
        return traceService.findOwnedTrace(principal.customerId(), traceId);
    }
}
