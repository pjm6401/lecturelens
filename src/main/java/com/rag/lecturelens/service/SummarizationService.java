package com.rag.lecturelens.service;

import com.rag.lecturelens.entity.LectureChunk;
import com.rag.lecturelens.openai.OpenAiClient;
import com.rag.lecturelens.repository.LectureChunkRepository;
import com.rag.lecturelens.util.EmbeddingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SummarizationService {

    private final LectureChunkRepository chunkRepository;
    private final OpenAiEmbeddingService embeddingService;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    // 1) 기존: lectureId 기반 요약 (DB에서 알아서 가져오는 버전)
    public String summarizeLecture(String userId, Long lectureId) {
        String ctx = buildContextFromLecture(lectureId);
        return summarizeFromContext(ctx);
    }

    // 2) 신규: LectureSummarizationService 등이 사용할 “공용 프롬프트 메서드”
    public String summarizeFromContext(String ctx) {
        String prompt = buildPrompt(ctx);
        return callOpenAi(prompt);
    }

    // ===== 아래부터는 내부 유틸 =====

    // lectureId 기준으로 topN chunk 뽑아서 context 만드는 부분 (지금 코드 그대로 이동)
    private String buildContextFromLecture(Long lectureId) {

        String wholeText = chunkRepository.findTop20ByLectureIdOrderByImportanceScoreDesc(lectureId)
                .stream()
                .map(LectureChunk::getText)
                .reduce("", (a, b) -> a + "\n" + b);

        float[] queryVec = embeddingService.embed(wholeText);
        String queryLiteral = EmbeddingUtils.toPgVectorLiteral(queryVec);

        List<LectureChunk> chunks = chunkRepository.searchTopN(lectureId, queryLiteral, 20);

        StringBuilder ctx = new StringBuilder();
        for (LectureChunk c : chunks) {
            ctx.append("### Chunk\n");
            ctx.append(c.getText()).append("\n\n");
        }
        return ctx.toString();
    }

    // 프롬프트 문자열을 만드는 부분 (지금 SummarizationService의 prompt 그대로)
    private String buildPrompt(String ctx) {
        return """
        너는 강의를 요약하는 전문 조교이며, Retrieval-Augmented Generation(RAG) 시스템 위에서 동작한다.
        
        중요 규칙:
        - 제공된 청크(context)에 없는 내용은 절대로 생성하지 않는다.
        - 모호하거나 불완전한 내용은 “자료에 명확히 언급되지 않음”이라고 표현한다.
        - 외부 지식을 임의로 사용하지 않는다.
        - 청크 간 충돌이 있을 경우, 가장 많이 등장하거나 논리적으로 일관된 내용을 선택한다.
        - 만약 중요한 내용에 설명이 부족한경우에만 외부 지식 사용을 허용한다. 단 블로그 같은 개인 포스팅이 아닌 논문이나 공식문서같은 신뢰할 수 있는 출처만 허용한다.
        
        내부 추론 규칙(출력하지 말 것):
        1) 청크들을 먼저 개념적으로 분류한다.
        2) 강의 흐름(도입 → 본론 → 정리)을 분석한다.
        3) 반복되는 키워드나 시험 가능성이 높은 개념을 추출한다.
        4) 그 후 최종 요약을 생성한다.
        
        출력 형식 예시 (반드시 따를 것):
        
        1. 자료구조의 개념
        1-1. 배열(Array)
        • 정의: 동일한 자료형을 연속된 공간에 저장하는 구조
        • 특징: 인덱스를 통한 O(1) 접근이 가능
        시험 포인트: 배열은 삽입/삭제 시 O(n) 시간이 소요됨
        
        아래 텍스트는 약 1시간 내외 강의의 핵심 발췌본(청크들)이다.
        이 내용을 기반으로 **시험 대비용 상세 요약 노트**를 작성하라.
        
        요구사항:
        1) A4 1~3장 분량의 요약을 작성한다. (너무 과도하게 압축하지도, 지나치게 늘리지도 말 것)
        2) 큰 제목(1단계), 소제목(2단계)을 활용해 섹션 구조를 만든다.
        3) 각 소제목 아래에는 bullet 형태로 주요 개념, 정의, 특징, 예시를 정리한다.
        4) 시험 포인트는 "시험 포인트"로 별도 줄에 표시한다.
        5) 수식과 용어는 한 줄을 넘기지 않도록 간결하게 설명하며, 원 용어(영문)는 괄호 안에 병기한다.
        6) 전체 문장은 자연스러운 한국어의 설명체로 작성한다.
        
        --------------------
        %s
        --------------------
        """.formatted(ctx);
    }


    // OpenAI 호출 공통 로직
    private String callOpenAi(String prompt) {
        try {
            String payload = """
                {
                  "model": "gpt-4.1",
                  "messages": [
                    {"role": "system", "content": "너는 한국 대학 강의를 요약하는 전문가이다."},
                    {"role": "user", "content": %s}
                  ],
                  "temperature": 0.2
                  "max_tokens": 2000
                }
            """.formatted(objectMapper.writeValueAsString(prompt));

            String response = openAiClient.postJson("/chat/completions", payload);
            JsonNode root = objectMapper.readTree(response);
            return root.get("choices").get(0).get("message").get("content").asText();

        } catch (Exception e) {
            throw new RuntimeException("요약 실패", e);
        }
    }
}
