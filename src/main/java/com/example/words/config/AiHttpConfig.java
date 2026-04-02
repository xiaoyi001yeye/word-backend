package com.example.words.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AiHttpConfig {

    @Bean
    public RestTemplate aiRestTemplate(
            RestTemplateBuilder builder,
            @Value("${ai.http.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${ai.http.read-timeout-ms:30000}") long readTimeoutMs) {
        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }
}
