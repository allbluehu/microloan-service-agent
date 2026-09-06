package com.example.microloan.ticket;

import com.example.microloan.agent.ChatMessageView;
import com.example.microloan.agent.SensitiveTextSanitizer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** 独立坐席权限下的工单队列、上下文与受控处理接口。 */
@RestController
@RequestMapping("/api/v1/support/tickets")
public class SupportController {
    private final TicketMapper mapper;
    public SupportController(TicketMapper mapper) { this.mapper = mapper; }

    @GetMapping
    public List<ServiceTicket> queue(@RequestParam(defaultValue="OPEN") String status) {
        if (!List.of("OPEN", "IN_PROGRESS", "RESOLVED").contains(status)) {
            throw new IllegalArgumentException("不支持的工单状态");
        }
        return mapper.findQueue(status);
    }

    @GetMapping("/{ticketId}/context")
    public List<ChatMessageView> context(@PathVariable String ticketId) { return mapper.findContext(ticketId); }

    @PostMapping("/{ticketId}/transition")
    @Transactional
    public void transition(@PathVariable String ticketId, @Valid @RequestBody Transition request) {
        // 条件更新防止两个坐席覆盖彼此操作；审计与状态变更在同一事务提交。
        String next = switch (request.expectedStatus()) {
            case "OPEN" -> "IN_PROGRESS";
            case "IN_PROGRESS" -> "RESOLVED";
            default -> throw new IllegalArgumentException("工单不能继续流转");
        };
        if (mapper.transition(ticketId, request.expectedStatus(), next) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "工单状态已变化，请刷新");
        }
        mapper.audit(ticketId, request.expectedStatus(), next,
                SensitiveTextSanitizer.redactAndLimit(request.note(), 500));
    }

    public record Transition(
            /** 操作前预期状态，用于并发冲突检测。 */
            @NotBlank String expectedStatus,
            /** 坐席处理说明，写入前脱敏。 */
            @NotBlank @Size(max=500) String note
    ) {}
}
