package com.example.microloan.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    private final ApplicationMapper applicationMapper;
    private final ObjectMapper objectMapper;

    public ApplicationService(ApplicationMapper applicationMapper, ObjectMapper objectMapper) {
        this.applicationMapper = applicationMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ApplicationStatus getStatus(Long authenticatedCustomerId, String applicationId) {
        ApplicationRow row = applicationMapper.findByApplicationNo(applicationId);
        // Missing and foreign applications intentionally share one response to prevent identifier probing.
        if (row == null || !row.customerId().equals(authenticatedCustomerId)) {
            throw new ApplicationAccessDeniedException();
        }
        return new ApplicationStatus(row.applicationNo(), row.productCode(), row.status(), row.reasonCode(),
                parseMissingDocuments(row.missingDocuments()), row.updatedAt());
    }

    private List<String> parseMissingDocuments(String json) {
        try {
            return List.copyOf(objectMapper.readValue(json, new TypeReference<List<String>>() {}));
        } catch (JsonProcessingException exception) {
            // Corrupt business data must fail closed rather than produce an incomplete customer answer.
            throw new IllegalStateException("Invalid missing_documents data", exception);
        }
    }
}
