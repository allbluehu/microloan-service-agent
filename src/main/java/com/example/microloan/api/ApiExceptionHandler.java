package com.example.microloan.api;

import com.example.microloan.repayment.LoanAccessDeniedException;
import java.util.UUID;
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "请求参数不合法");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String code = status == HttpStatus.UNAUTHORIZED ? "AUTHENTICATION_FAILED" : "REQUEST_REJECTED";
        return problem(status, code, exception.getReason() == null ? status.getReasonPhrase() : exception.getReason());
    }

    private ResponseEntity<ApiError> problem(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message, UUID.randomUUID().toString()));
    }
}
