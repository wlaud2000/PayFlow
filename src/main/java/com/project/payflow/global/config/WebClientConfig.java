package com.project.payflow.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig{

    private final TossPaymentsProperties tossPaymentsProperties;

    @Bean
    public WebClient tossPaymentsWebClient(){
        String credentials = Base64.getEncoder().encodeToString(
                (tossPaymentsProperties.getSecretKey() + ":").getBytes(StandardCharsets.UTF_8)
        );

        return WebClient.builder()
                .baseUrl(tossPaymentsProperties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}