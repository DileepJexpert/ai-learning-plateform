package com.dileep.ailearning.compliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Ingestion request payload for a regulatory circular containing clauses.
 */
public record IngestCircularRequest(
        @NotBlank(message = "circularId cannot be blank") String circularId,
        @NotBlank(message = "circularTitle cannot be blank") String circularTitle,
        @NotBlank(message = "authority cannot be blank") String authority,
        @NotBlank(message = "effectiveDate cannot be blank") String effectiveDate,
        @NotEmpty(message = "clauses cannot be empty") List<RawClause> clauses
) {
    public record RawClause(
            @NotBlank String clauseRef,
            @NotBlank String sectionTitle,
            @NotBlank String content
    ) {
    }
}
