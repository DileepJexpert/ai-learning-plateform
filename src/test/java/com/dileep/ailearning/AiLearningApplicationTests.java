package com.dileep.ailearning;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: the full Spring context wires up cleanly (beans, config properties,
 * RestClient, DataSource). Does NOT call Ollama — startup never makes a model
 * request — so it works without a running model.
 *
 * <p>Uses the "integration" profile which points at the Docker Compose pgvector.
 * Start it first: {@code docker compose up -d}.
 */
@SpringBootTest
@ActiveProfiles("integration")
class AiLearningApplicationTests {

    @Test
    void contextLoads() {
    }
}
