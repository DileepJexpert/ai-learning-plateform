package com.dileep.ailearning.production.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for production chat endpoint with optional cache-bypass and custom steering.
 */
public record ProductionChatRequest(
        @NotBlank(message = "prompt cannot be blank") String prompt,
        String system,
        Boolean bypassCache,
        Double temperature
) {
}
