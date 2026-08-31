package com.example.microloan.application;

import java.time.OffsetDateTime;
import java.util.List;

public record ApplicationStatus(
        String applicationId,
        String productCode,
        String status,
        String reasonCode,
        List<String> missingDocuments,
        OffsetDateTime updatedAt
) {}
