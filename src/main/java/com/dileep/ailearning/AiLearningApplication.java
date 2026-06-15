package com.dileep.ailearning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Applied AI for backend engineers — a runnable reference project.
 *
 * <p>This single Spring Boot app currently covers:
 * <ul>
 *   <li><b>Module 0 — Baseline:</b> a thin client that calls a local Ollama
 *       model over HTTP ({@code /api/chat}). See the {@code ollama} and
 *       {@code chat} packages.</li>
 *   <li><b>Module 1 — Prompt engineering &amp; structured output:</b> an invoice
 *       extractor that is hardened to return valid, schema-correct JSON every
 *       time (strict schema + few-shot + null handling + auto-retry). See the
 *       {@code invoice} package.</li>
 * </ul>
 *
 * <p>{@link ConfigurationPropertiesScan} picks up our typed config records
 * (e.g. {@code OllamaProperties}) without an explicit {@code @EnableConfigurationProperties}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AiLearningApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiLearningApplication.class, args);
    }
}
