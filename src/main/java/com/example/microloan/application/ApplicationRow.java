package com.example.microloan.application;

import java.time.OffsetDateTime;

public record ApplicationRow(
        String applicationNo,
        Long customerId,
        String productCode,
        String status,
        String reasonCode,
        String missingDocuments,
        OffsetDateTime updatedAt
) {}
