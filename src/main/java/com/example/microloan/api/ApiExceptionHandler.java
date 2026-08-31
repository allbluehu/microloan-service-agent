package com.example.microloan.api;

import com.example.microloan.repayment.LoanAccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(LoanAccessDeniedException.class)
    ProblemDetail handleLoanAccessDenied(LoanAccessDeniedException exception, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "LOAN_ACCESS_DENIED", exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "请求参数不合法", request);
    }

    private ProblemDetail problem(HttpStatus status, String code, String message, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(code);
        detail.setProperty("code", code);
        detail.setProperty("traceId", UUID.randomUUID().toString());
        detail.setProperty("path", request.getRequestURI());
        return detail;
    }
}
