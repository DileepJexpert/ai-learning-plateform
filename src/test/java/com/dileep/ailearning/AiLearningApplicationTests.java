package com.dileep.ailearning;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: the Spring context wires up cleanly (beans, config properties,
 * RestClient). This does NOT call Ollama — startup never makes a model request —
 * so it passes with or without Ollama running.
 */
@SpringBootTest
class AiLearningApplicationTests {

    @Test
    void contextLoads() {
    }
}
