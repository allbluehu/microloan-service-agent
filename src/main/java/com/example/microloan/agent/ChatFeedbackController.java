package com.example.microloan.agent;

import com.example.microloan.security.CustomerPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/messages")
public class ChatFeedbackController {
    private final ChatFeedbackService feedbackService;

    public ChatFeedbackController(ChatFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/{messageId}/feedback")
    public ChatFeedbackView submit(@AuthenticationPrincipal CustomerPrincipal principal,
                                   @PathVariable String messageId,
                                   @Valid @RequestBody FeedbackRequest request) {
        return feedbackService.submit(principal.customerId(), messageId,
                request.rating(), request.reason(), request.comment());
    }

    public record FeedbackRequest(
            /** 评价方向，必填。 */
            @NotNull FeedbackRating rating,
            /** 评价原因，可为空。 */
            FeedbackReason reason,
            /** 补充说明，可为空，最多 500 个字符。 */
            @Size(max = 500) String comment
    ) {}
}
