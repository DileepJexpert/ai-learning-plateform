package com.dileep.ailearning.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Wires the {@link RestClient} we use to call Ollama.
 *
 * <p>We deliberately use a plain HTTP client against Ollama's native REST API
 * (rather than a higher-level SDK) so the wire format stays visible — that is
 * the point of Module 0: understand exactly what request goes to the model.
 */
@Configuration
public class OllamaClientConfig {

    @Bean
    public RestClient ollamaRestClient(OllamaProperties properties) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.readTimeout().toMillis());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
