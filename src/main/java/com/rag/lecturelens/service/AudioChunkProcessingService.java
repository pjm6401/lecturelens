package com.rag.lecturelens.service;

import com.rag.lecturelens.entity.AudioChunk;
import com.rag.lecturelens.entity.Lecture;
import com.rag.lecturelens.repository.AudioChunkRepository;
import com.rag.lecturelens.repository.LectureRepository;
import com.rag.lecturelens.util.EmbeddingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioChunkProcessingService {

    private static final int MAX_CHARS_PER_CHUNK = 1000;   // 🔥 너무 긴 텍스트는 이 길이 기준으로 여러 청크로 분할

    private final LectureRepository lectureRepository;
    private final AudioChunkRepository audioChunkRepository;
    private final AudioSttService audioSttService;
    private final OpenAiEmbeddingService embeddingService;

    /**
     * 1) Lecture에서 originalAudioPath 확인
     * 2) AudioSttService가 S3에서 파일 읽어서 STT 수행 → transcript 문자열
     * 3) transcript를 문장/문단 단위로 분리
     * 4) 각 조각을 AudioChunk로 저장 (embedding 포함)
     */
    @Transactional
    public void processAudioToChunks(Lecture lecture) {

        List<String> audioKeys = lecture.getConvertedAudioPath();
        Long lectureId = lecture.getId();

        if (audioKeys == null || audioKeys.isEmpty()) {
            audioKeys = lecture.getOriginalAudioPath();
        }

        if (audioKeys == null || audioKeys.isEmpty()) {
            log.info("🎤 Audio 미존재");
            return;
        }

        for (String audioKey : audioKeys) {
            log.info("🎤 Audio → Chunk 처리 시작: lectureId={}, audioKey={}", lectureId, audioKey);

            // ✅ 1) STT 수행
            String transcript = audioSttService.transcribeLecture(audioKey, lecture.getId());

            if (transcript == null || transcript.isBlank()) {
                log.warn("⚠ STT 결과가 비어있음: lectureId={}, audioKey={}", lectureId, audioKey);
                continue;
            }

            log.info("🎤 STT 완료. 길이={} chars", transcript.length());

            // ✅ 2) transcript → 문장/문단 단위로 분리
            List<String> chunks = splitTranscriptIntoChunks(transcript);
            log.info("🎤 transcript를 {}개의 1차 청크(문장 단위)로 분할", chunks.size());

            int created = 0;

            for (String raw : chunks) {
                String text = raw.trim();
                if (text.isEmpty()) continue;

                // 🔥 이전: 너무 긴 문장을 1000자에서 잘라버렸음
                // if (text.length() > 1000) {
                //     text = text.substring(0, 1000);
                // }

                // ✅ 수정: 너무 긴 문장은 여러 청크로 분할해서 각각 저장
                if (text.length() > MAX_CHARS_PER_CHUNK) {
                    List<String> parts = splitLongText(text, MAX_CHARS_PER_CHUNK);
                    for (String part : parts) {
                        saveAudioChunk(lecture, part);
                        created++;
                    }
                } else {
                    saveAudioChunk(lecture, text);
                    created++;
                }
            }

            log.info("✅ Audio Chunk 생성 완료: lectureId={}, count={}", lectureId, created);
        }

    }

    /**
     * STT 결과 텍스트를 "문장 단위" 또는 "짧은 문단 단위"로 나누는 간단한 유틸.
     * - 마침표/물음표/느낌표/줄바꿈 기준으로 자름.
     */
    private List<String> splitTranscriptIntoChunks(String transcript) {
        List<String> result = new ArrayList<>();
        if (transcript == null || transcript.isBlank()) {
            return result;
        }

        // 1차: 줄바꿈 단위로 먼저 분리
        String[] paragraphs = transcript.split("\\r?\\n+");
        for (String para : paragraphs) {
            String p = para.trim();
            if (p.isEmpty()) continue;

            // 2차: 문장 단위로 분리 (. ? ! … 등 기준)
            String[] sentences = p.split("(?<=[\\.\\?\\!…])\\s+");
            for (String s : sentences) {
                String sentence = s.trim();
                if (!sentence.isEmpty()) {
                    result.add(sentence);
                }
            }
        }

        return result;
    }

    /**
     * 🔧 너무 긴 텍스트를 MAX_CHARS_PER_CHUNK 기준으로 여러 조각으로 나누는 유틸
     *  - "잘라버리는" 게 아니라 → 여러 AudioChunk로 나누어 저장하기 위함
     */
    private List<String> splitLongText(String text, int maxLength) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        int len = text.length();

        while (start < len) {
            int end = Math.min(start + maxLength, len);
            parts.add(text.substring(start, end));
            start = end;
        }
        return parts;
    }

    /**
     * 🔁 공통 AudioChunk 저장 로직
     */
    private void saveAudioChunk(Lecture lecture, String text) {
        // 1) 임베딩 생성
        float[] vec = embeddingService.embed(text);
        String embeddingLiteral = EmbeddingUtils.toPgVectorLiteral(vec);

        // 2) AudioChunk 저장
        AudioChunk chunk = AudioChunk.builder()
                .lecture(lecture)
                .startSec(null)       // 필요하면 나중에 타임라인 추가
                .endSec(null)
                .text(text)
                .importanceScore(0.7f)
                .embedding(embeddingLiteral)
                .build();

        audioChunkRepository.save(chunk);
    }
}
