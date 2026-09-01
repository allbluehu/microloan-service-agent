package com.example.microloan.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.microloan.agent.ToolUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ApiExceptionHandlerTest {

    @Test
    void hidesInternalToolFailureBehindStableServiceUnavailableError() {
        ResponseEntity<ApiError> response = new ApiExceptionHandler()
                .handleToolUnavailable(new ToolUnavailableException(new RuntimeException("internal detail")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("TOOL_UNAVAILABLE");
        assertThat(response.getBody().message()).doesNotContain("internal detail");
        assertThat(response.getBody().traceId()).isNotBlank();
    }
}
