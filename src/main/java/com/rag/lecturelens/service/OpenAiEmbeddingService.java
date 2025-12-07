package com.rag.lecturelens.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
@RequiredArgsConstructor
public class OpenAiEmbeddingService {

    @Value("${openai.api.key}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public float[] embed(String text) {
        try {
            String body = """
            {
              "model": "text-embedding-3-small",
              "input": %s
            }
            """.formatted(objectMapper.writeValueAsString(text));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/embeddings"))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                throw new RuntimeException("Embedding API Error: " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode vectorNode = root.path("data").get(0).path("embedding");

            float[] embedding = new float[vectorNode.size()];
            for (int i = 0; i < vectorNode.size(); i++) {
                embedding[i] = (float) vectorNode.get(i).asDouble();
            }

            return embedding;

        } catch (Exception e) {
            throw new RuntimeException("Embedding 생성 오류", e);
        }
    }
}
