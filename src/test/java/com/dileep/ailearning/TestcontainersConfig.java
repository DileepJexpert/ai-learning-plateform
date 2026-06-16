package com.dileep.ailearning;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts a real Postgres+pgvector instance in Docker via Testcontainers and wires it
 * as the test DataSource. Use this in any {@code @SpringBootTest} that needs a
 * database: {@code @Import(TestcontainersConfig.class)}.
 *
 * <p>If Testcontainers can't find Docker (e.g. in some CI/cloud environments),
 * the fallback is to start pgvector yourself ({@code docker compose up -d}) and
 * use the "integration" profile instead. See {@code application-integration.yml}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    @SuppressWarnings("resource")
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg16")
                        .asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("ai_learning_test")
                .withUsername("test")
                .withPassword("test");
    }
}
