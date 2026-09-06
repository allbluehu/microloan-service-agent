package com.example.microloan.agent;

import com.example.microloan.security.CustomerPrincipal;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** 长会话只恢复已选择实体和最近意图，不把历史金额或模型总结作为业务事实。 */
@RestController
@RequestMapping("/api/v1/chat/sessions")
public class ChatContextController {
    private final ChatHistoryMapper mapper;
    public ChatContextController(ChatHistoryMapper mapper) { this.mapper = mapper; }

    @GetMapping("/{sessionId}/context")
    public ContextView context(@AuthenticationPrincipal CustomerPrincipal principal, @PathVariable String sessionId) {
        var context = mapper.findOwnedSessionContext(sessionId, principal.customerId());
        if (context == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        // 只在有限窗口内摘要主题，避免长历史进入模型和重复消耗 Token。
        List<String> intents = mapper.findRecentOwnedMessages(principal.customerId(), sessionId, 100).stream()
                .filter(m -> m.intent() != null).map(ChatMessageView::intent).distinct().limit(5).toList();
        return new ContextView(context.selectedLoanId(), context.selectedApplicationId(),
                context.selectedProductCode(), context.selectedRegion(), intents);
    }

    @DeleteMapping("/{sessionId}/context")
    public void clear(@AuthenticationPrincipal CustomerPrincipal principal, @PathVariable String sessionId) {
        if (mapper.clearOwnedContext(principal.customerId(), sessionId) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        }
    }

    public record ContextView(
            /** 已选择贷款，不包含历史账单事实。 */ String selectedLoanId,
            /** 已选择申请。 */ String selectedApplicationId,
            /** 已选择产品。 */ String selectedProductCode,
            /** 已选择地区。 */ String selectedRegion,
            /** 最近一百条消息涉及的至多五个意图，按最近出现排序。 */ List<String> recentIntents
    ) {}
}
