package com.dileep.ailearning.compliance.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Audit compliance query request payload.
 */
public record AuditComplianceQueryRequest(
        @NotBlank(message = "query cannot be blank") String query,
        String circularIdFilter,
        Integer topK
) {
    public int resolvedTopK() {
        return topK != null && topK > 0 ? topK : 5;
    }
}
