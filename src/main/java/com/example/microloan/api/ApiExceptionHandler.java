package com.example.microloan.api;

import com.example.microloan.application.ApplicationAccessDeniedException;
import com.example.microloan.agent.ToolUnavailableException;
import com.example.microloan.knowledge.EmbeddingModelUnavailableException;
import com.example.microloan.repayment.LoanAccessDeniedException;
import com.example.microloan.settlement.StepUpAuthRequiredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(LoanAccessDeniedException.class)
    ResponseEntity<ApiError> handleLoanAccessDenied(LoanAccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "LOAN_ACCESS_DENIED", exception.getMessage());
    }

    @ExceptionHandler(ApplicationAccessDeniedException.class)
    ResponseEntity<ApiError> handleApplicationAccessDenied(ApplicationAccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "APPLICATION_ACCESS_DENIED", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "请求参数不合法");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", exception.getMessage());
    }

    @ExceptionHandler(StepUpAuthRequiredException.class)
    ResponseEntity<ApiError> handleStepUpRequired(StepUpAuthRequiredException exception) {
        return problem(HttpStatus.FORBIDDEN, "STEP_UP_AUTH_REQUIRED", exception.getMessage());
    }

    @ExceptionHandler(ToolUnavailableException.class)
    ResponseEntity<ApiError> handleToolUnavailable(ToolUnavailableException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "TOOL_UNAVAILABLE", exception.getMessage());
    }

    @ExceptionHandler(EmbeddingModelUnavailableException.class)
    ResponseEntity<ApiError> handleEmbeddingModelUnavailable(EmbeddingModelUnavailableException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "EMBEDDING_MODEL_UNAVAILABLE", exception.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String code = status == HttpStatus.UNAUTHORIZED ? "AUTHENTICATION_FAILED" : "REQUEST_REJECTED";
        return problem(status, code, exception.getReason() == null ? status.getReasonPhrase() : exception.getReason());
    }

    private ResponseEntity<ApiError> problem(HttpStatus status, String code, String message) {
        // 复用请求级标识，使错误响应、响应头和同一请求日志能够直接关联。
        return ResponseEntity.status(status).body(new ApiError(
                code, message, RequestTraceContext.currentOrCreate()));
    }
}
