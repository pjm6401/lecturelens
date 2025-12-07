package com.rag.lecturelens.service;

import com.rag.lecturelens.domain.LectureStatus;
import com.rag.lecturelens.entity.Lecture;
import com.rag.lecturelens.entity.LectureChunk;
import com.rag.lecturelens.repository.LectureChunkRepository;
import com.rag.lecturelens.util.EmbeddingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfLectureProcessingService {

    private final LectureChunkRepository lectureChunkRepository;
    private final S3StorageService s3StorageService;
    private final OpenAiEmbeddingService openAiEmbeddingService;
    /**
     * 1) S3에서 PDF 다운로드
     * 2) PDF 페이지별 텍스트 추출
     * 3) LectureChunk 생성/저장
     * 4) 상태 READY 변경
     */
    @Transactional(noRollbackFor = Exception.class)
    public void processLecture(Lecture lecture) {

        // 변환된 PDF 우선 사용
        List<String> pdfKeys = lecture.getConvertedPdfPath();
        String lectureId = String.valueOf(lecture.getId());
        if (pdfKeys == null || pdfKeys.isEmpty()) {
            pdfKeys = lecture.getOriginalPdfPath();
        }

        if (pdfKeys == null || pdfKeys.isEmpty()) {
            log.info(" 강의자료 미존재");
            return;
        }

        for(String pdfKey : pdfKeys){
            if (pdfKey == null || pdfKey.isEmpty()) {
                lecture.setStatus(LectureStatus.FAILED);
                throw new IllegalStateException("PDF 경로가 설정되어 있지 않습니다.");
            }
            log.info("📄 PDF 처리 시작 → lectureId={}, key={}", lectureId, pdfKey);
            // 기존 chunk 삭제 후 재생성
            lectureChunkRepository.deleteByLecture(lecture);
            try (ResponseInputStream<GetObjectResponse> inputStream = s3StorageService.getObjectStream(pdfKey);
                 PDDocument document = PDDocument.load(inputStream)) {

                PDFTextStripper stripper = new PDFTextStripper();
                int pageCount = document.getNumberOfPages();
                log.info("📝 PDF 페이지 수: {}", pageCount);

                for (int page = 1; page <= pageCount; page++) {
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);

                    String pageText = stripper.getText(document);
                    if (pageText == null || pageText.trim().isEmpty()) {
                        continue;
                    }
                    String cleanText = pageText.trim();
                    // 길이 제한 (2000자)
                    if (cleanText.length() > 2000) {
                        cleanText = cleanText.substring(0, 2000);
                    }

                    // 1) 청크 임베딩 생성
                    float[] embeddingVector = openAiEmbeddingService.embed(cleanText);
                    float[] vec = openAiEmbeddingService.embed(pageText.trim());

                    // ✅ 2) String 리터럴로 변환
                    String embeddingLiteral = EmbeddingUtils.toPgVectorLiteral(vec);
                    // 2) Chunk 저장
                    LectureChunk chunk = LectureChunk.builder()
                            .lecture(lecture)
                            .slideNo(page)
                            .startSec(null)
                            .endSec(null)
                            .text(cleanText)
                            .importanceScore(0.5f)
                            .embedding(embeddingLiteral)
                            .build();

                    lectureChunkRepository.save(chunk);
                }

                lecture.setStatus(LectureStatus.READY);
                log.info("✅ PDF 처리 완료: lectureId={}", lectureId);

            } catch (IOException e) {
                lecture.setStatus(LectureStatus.FAILED);
                log.error("❌ PDF 처리 실패: lectureId={}", lectureId, e);
                throw new RuntimeException("PDF 처리 실패", e);
            }
        }
    }
}
