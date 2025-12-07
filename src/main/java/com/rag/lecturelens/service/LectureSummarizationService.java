package com.rag.lecturelens.service;


import com.rag.lecturelens.entity.AudioChunk;
import com.rag.lecturelens.entity.Lecture;
import com.rag.lecturelens.entity.LectureChunk;
import com.rag.lecturelens.repository.AudioChunkRepository;
import com.rag.lecturelens.repository.LectureChunkRepository;
import com.rag.lecturelens.repository.LectureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LectureSummarizationService {

    private final LectureRepository lectureRepository;
    private final LectureChunkRepository lectureChunkRepository;
    private final AudioChunkRepository audioChunkRepository;
    private final SummarizationService summarizationService;

    @Value("${openai.api.key}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();


    public String summarizeLectureWithAudioAndPdf(Long lectureId) {

        // 1) PDF 청크 + 오디오 청크 텍스트 가져오기
        List<String> texts = new ArrayList<>();

        lectureChunkRepository.findByLecture_Id(lectureId)
                .forEach(c -> texts.add(c.getText()));

        audioChunkRepository.findByLecture_Id(lectureId)
                .forEach(c -> texts.add(c.getText()));

        // 2) SummarizationService에서 쓰는 ctx 포맷과 동일하게 구성
        StringBuilder ctx = new StringBuilder();
        for (String t : texts) {
            ctx.append("### Chunk\n")
                    .append(t)
                    .append("\n\n");
        }

        // 3) 프롬프트 + OpenAI 호출은 SummarizationService에 위임
        return summarizationService.summarizeFromContext(ctx.toString());
    }

    /**
     * PDF 슬라이드 청크 + 오디오 STT 청크를 함께 사용해서
     * OpenAI에게 "시험 대비용 강의 요약"을 요청한다.
     */
    public String summarizeLecture(String userId, Long lectureId) {

        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new IllegalArgumentException("Lecture not found: " + lectureId));

        // 1) 슬라이드 텍스트 상위 N개
        List<LectureChunk> slideChunks =
                lectureChunkRepository.findTop20ByLectureIdOrderByImportanceScoreDesc(lectureId);

        // 중요도 기반이 없다면 슬라이드 번호순으로 가져오는 fallback도 고려 가능
        if (slideChunks.isEmpty()) {
            slideChunks =
                    lectureChunkRepository.findTop20ByLectureIdOrderBySlideNoAsc(lectureId);
        }

        // 2) 오디오 텍스트 상위 N개
        List<AudioChunk> audioChunks =
                audioChunkRepository.findTop30ByLectureIdOrderByImportanceScoreDesc(lectureId);

        if (audioChunks.isEmpty()) {
            audioChunks =
                    audioChunkRepository.findTop30ByLectureIdOrderByStartSecAsc(lectureId);
        }

        if (slideChunks.isEmpty() && audioChunks.isEmpty()) {
            return "요약할 수 있는 강의 데이터가 없습니다. (슬라이드/오디오 청크 없음)";
        }

        // 3) 슬라이드/오디오 텍스트를 요약 프롬프트용으로 합치기
        String contextText = buildContextText(slideChunks, audioChunks);

        try {
            String prompt = buildUserPrompt(lecture.getTitle(), contextText);

            String requestBodyJson = """
            {
              "model": "gpt-4.1-mini",
              "messages": [
                {
                  "role": "system",
                  "content": "너는 대학 강의 요약을 돕는 AI 튜터야. 학생이 복습과 시험 준비를 쉽게 할 수 있도록, 핵심 개념과 예시 중심으로 내용을 구조화해서 정리해줘. 답변은 반드시 한국어로 작성해."
                },
                {
                  "role": "user",
                  "content": %s
                }
              ],
              "temperature": 0.3
            }
            """.formatted(objectMapper.writeValueAsString(prompt));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                log.error("❌ OpenAI Summarization API Error: status={}, body={}",
                        response.statusCode(), response.body());
                throw new RuntimeException("OpenAI Summarization API Error: " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new RuntimeException("OpenAI 응답 형식이 예상과 다릅니다: " + response.body());
            }

            String summary = choices.get(0).path("message").path("content").asText();
            log.info("✅ Lecture {} summarization 성공", lectureId);
            return summary;

        } catch (Exception e) {
            log.error("요약 생성 중 오류 발생: lectureId={}", lectureId, e);
            throw new RuntimeException("강의 요약 생성 실패", e);
        }
    }

    /**
     * 슬라이드 청크 + 오디오 청크에서 텍스트를 뽑아
     * 한 번에 프롬프트에 넣을 수 있도록 문자열로 합친다.
     * 너무 길어질 수 있으니 일부만 자른다.
     */
    private String buildContextText(List<LectureChunk> slideChunks,
                                    List<AudioChunk> audioChunks) {

        StringBuilder sb = new StringBuilder();

        sb.append("【슬라이드 내용 요약 후보】\n");
        int maxSlides = Math.min(slideChunks.size(), 10); // 상위 10개까지만
        for (int i = 0; i < maxSlides; i++) {
            LectureChunk c = slideChunks.get(i);
            sb.append("- [Slide ").append(c.getSlideNo()).append("]\n");
            sb.append(trim(c.getText(), 600)).append("\n\n");
        }

        sb.append("\n【강의 음성(STT) 내용 요약 후보】\n");
        int maxAudios = Math.min(audioChunks.size(), 15);
        for (int i = 0; i < maxAudios; i++) {
            AudioChunk a = audioChunks.get(i);
            sb.append("- [")
                    .append(formatTime(a.getStartSec()))
                    .append(" ~ ")
                    .append(formatTime(a.getEndSec()))
                    .append("]\n");
            sb.append(trim(a.getText(), 400)).append("\n\n");
        }

        return sb.toString();
    }

    /**
     * 실제 user 프롬프트: 어떤 형식으로 요약해달라는지 명확히 적는다.
     */
    private String buildUserPrompt(String title, String context) {
        return """
        아래는 대학 강의 "%s"의 슬라이드 텍스트와 강의 음성(STT)에서 추출한 핵심 내용입니다.

        --------------------
        %s
        --------------------

        위 내용을 바탕으로, 다음 형식에 맞춰 시험 대비용 요약 노트를 만들어줘.

        1. 강의 전체 개요 (3~5줄)
        2. 반드시 기억해야 할 핵심 개념 5~10개
           - 각 개념마다 2~3줄 설명
        3. 교수님이 강조한 부분 정리
           - "강조", "중요", "시험" 등의 표현이 나타나는 부분을 우선적으로 포함
        4. 학생이 스스로 복습할 수 있는 체크리스트
           - "~을 설명할 수 있는가?"
           - "~의 차이점을 말할 수 있는가?" 형태 5~8개

        문단과 리스트를 적절히 섞어서, 읽기 편한 형식으로 출력해줘.
        """.formatted(title, context);
    }

    private String trim(String text, int maxLen) {
        if (text == null) return "";
        text = text.trim();
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }

    private String formatTime(Integer sec) {
        if (sec == null) return "N/A";
        int s = sec;
        int m = s / 60;
        int r = s % 60;
        return String.format("%02d:%02d", m, r);
    }
}
