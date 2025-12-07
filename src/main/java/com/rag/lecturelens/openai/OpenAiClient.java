package com.rag.lecturelens.openai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class OpenAiClient {

    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public OpenAiClient(
            ObjectMapper objectMapper,
            @Value("${openai.api.key}") String apiKey
    ) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        log.info("🔑 OpenAI WebClient 초기화 완료. key prefix={}", apiKey.substring(0, 8));
    }

    public String postJson(String path, String jsonBody) {
        return webClient.post()
                .uri(path)
                .bodyValue(jsonBody)
                .retrieve()
                .bodyToMono(String.class)
                .block(); // 동기 사용
    }
}
