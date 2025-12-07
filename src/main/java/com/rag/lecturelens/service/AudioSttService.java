package com.rag.lecturelens.service;

import com.rag.lecturelens.entity.Lecture;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AudioSttService {

    @Value("${openai.api.key}")
    private String apiKey;

    private final S3StorageService s3StorageService;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Lecture에 저장된 originalAudioPath (또는 convertedAudioPath)를 사용해서 STT 수행
     */
    public String transcribeLecture(String audioKey,Long lectureId) {


        byte[] audioBytes = s3StorageService.getObjectBytes(audioKey);

        // 파일명은 아무거나 가능, 확장자는 실제 포맷 기준으로
        String filename = "lecture-" + lectureId + ".mp4";

        return transcribeBytes(audioBytes, filename, "audio/mp4");
    }

    /**
     * S3에서 가져온 raw 바이트 배열을 Whisper API로 전송하는 핵심 메서드
     */
    public String transcribeBytes(byte[] audioBytes, String filename, String contentType) {
        try {
            String boundary = "----JavaFormBoundary" + UUID.randomUUID();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8), true);

            // ------- file part -------
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                    .append(filename).append("\"\r\n");
            writer.append("Content-Type: ").append(contentType).append("\r\n");
            writer.append("\r\n");
            writer.flush();

            // ⚠️ 여기서 진짜 오디오 바이트를 그대로 넣는 게 중요! (String 변환 금지)
            baos.write(audioBytes);
            baos.flush();

            writer.append("\r\n");
            writer.flush();

            // ------- model part -------
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"model\"\r\n\r\n");
            writer.append("gpt-4o-mini-transcribe").append("\r\n");
            writer.flush();

            // 필요하면 language, temperature 같은 옵션도 추가 가능
            // writer.append("--").append(boundary).append("\r\n");
            // writer.append("Content-Disposition: form-data; name=\"language\"\r\n\r\n");
            // writer.append("ko").append("\r\n");
            // writer.flush();

            // ------- end boundary -------
            writer.append("--").append(boundary).append("--").append("\r\n");
            writer.close();

            byte[] multipartBody = baos.toByteArray();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/audio/transcriptions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                // Whisper에서 실패시 응답 body 그대로 던져줘서 디버깅 쉽게
                throw new RuntimeException("STT 실패: " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            return root.path("text").asText();

        } catch (Exception e) {
            throw new RuntimeException("STT 실패", e);
        }
    }
}
