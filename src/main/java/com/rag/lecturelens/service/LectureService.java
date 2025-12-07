package com.rag.lecturelens.service;

import com.rag.lecturelens.dto.DownloadFile;
import com.rag.lecturelens.dto.LectureStatusResponse;
import com.rag.lecturelens.dto.UploadFileData;
import com.rag.lecturelens.entity.AppUser;
import com.rag.lecturelens.entity.Lecture;
import com.rag.lecturelens.domain.LectureStatus;
import com.rag.lecturelens.repository.AudioChunkRepository;
import com.rag.lecturelens.repository.LectureChunkRepository;
import com.rag.lecturelens.repository.LectureRepository;
import com.rag.lecturelens.util.PdfGenerator;
import com.rag.lecturelens.util.ValidateExtension;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LectureService {

    private final LectureRepository lectureRepository;
    private final S3StorageService s3StorageService;
    private final ConvertService convertService;
    private final PdfLectureProcessingService pdfLectureProcessingService;
    private final AudioChunkProcessingService audioChunkProcessingService;
    private final SummarizationService summarizationService;
    private final LectureSummarizationService lectureSummarizationService;
    private final PdfGenerator pdfGenerator;
    private final LectureChunkRepository lectureChunkRepository;
    private final AudioChunkRepository audioChunkRepository;
    private final ValidateExtension ValidateExtension;

    // LectureService
    @Transactional
    public Lecture createInitialLecture(AppUser user, String title, String description) {
        Lecture lecture = Lecture.builder()
                .user(user)
                .title(title)
                .description(description)
                .status(LectureStatus.PROCESSING)
                .build();
        return lectureRepository.save(lecture);
    }


    @Async
    public void processLectureAsync(
            Long lectureId,
            String userId,
            List<UploadFileData> documentFiles,
            List<UploadFileData> audioFiles
    ) {

        List<String> docExt = getExtension(documentFiles);
        List<String> audioExt = getExtension(audioFiles);

        for (String ext : docExt) {
            ValidateExtension.validateDocumentExtension(ext);
        }
        for (String ext : audioExt) {
            ValidateExtension.validateAudioExtension(ext);
        }

        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new IllegalArgumentException("Lecture not found: " + lectureId));

        try {
            // 1) 원본 S3 업로드 (UploadFileData → bytes 기반)
            List<String> docOriginalKey = s3StorageService.uploadOriginalBytes(
                    userId, lectureId, "original_", documentFiles
            );

            List<String> audioOriginalKey = s3StorageService.uploadOriginalBytes(
                    userId, lectureId, "original_", audioFiles
            );

            // 2) 변환 (UploadFileData 기반 convertService 사용)
            // 문서 → PDF
            List<byte[]> pdfBytesList = convertService.convertToPdfFromBytes(documentFiles);
            List<String> convertedPdfKey = s3StorageService.uploadConvertFile(
                    userId,
                    lectureId,
                    "converted_lecture.pdf",
                    pdfBytesList,
                    "application/pdf"
            );

            // 오디오 → MP4
            List<byte[]> mp4BytesList = convertService.convertToMp4FromBytes(audioFiles);
            List<String> convertedMp4Key = s3StorageService.uploadConvertFile(
                    userId,
                    lectureId,
                    "converted_lecture.mp4",
                    mp4BytesList,
                    "video/mp4"
            );

            // 3) DB에 S3 경로 저장
            lecture.updateStoragePaths(
                    docOriginalKey,
                    audioOriginalKey,
                    convertedPdfKey,
                    convertedMp4Key
            );
            lectureRepository.save(lecture);

            // 4) 청크 + 요약 + 결과 PDF S3 업로드
            pdfLectureProcessingService.processLecture(lecture);
            audioChunkProcessingService.processAudioToChunks(lecture);

            String summary = lectureSummarizationService.summarizeLectureWithAudioAndPdf(lectureId);

            byte[] summaryPdfBytes = pdfGenerator.generate(summary);
            String summarizationKey = s3StorageService.uploadResultFile(
                    userId,
                    lectureId,
                    lecture.getTitle(),
                    "summary.pdf",
                    summaryPdfBytes,
                    "application/pdf"
            );

            lecture.setSummarizationKey(summarizationKey);
            lecture.setStatus(LectureStatus.READY);
            lectureRepository.save(lecture);

        } catch (RuntimeException e) {
            lecture.setStatus(LectureStatus.FAILED);
            lectureRepository.save(lecture);
            log.error("강의 처리 실패 userId={}, lectureId={}", userId, lectureId, e);
        }
    }

    public DownloadFile downloadS3File(String userId, Long lectureId,String title){
        Lecture lecture = lectureRepository.findByIdAndUser_UserIdAndTitle(lectureId, userId,title)
                .orElseThrow(() -> new IllegalArgumentException("Lecture not found or no permission"));
        byte[] file =  s3StorageService.downloadFile(lecture.getSummarizationKey());

        String fileName = lecture.getSummarizationKey().substring(lecture.getSummarizationKey().lastIndexOf('/') + 1);
        return new DownloadFile(fileName, file);
    }

    @Transactional
    public void deleteLectureAndS3(String userId, Long lectureId, String title) {
        Lecture lecture = lectureRepository.findByIdAndUser_UserIdAndTitle(lectureId, userId, title)
                .orElseThrow(() -> new IllegalArgumentException("해당 강의를 찾을 수 없습니다."));

        if (lecture.getOriginalPdfPath() != null) {
            s3StorageService.deleteObjects(lecture.getOriginalPdfPath());
        }
        if (lecture.getOriginalAudioPath() != null) {
            s3StorageService.deleteObjects(lecture.getOriginalAudioPath());
        }
        if (lecture.getConvertedAudioPath() != null) {
            s3StorageService.deleteObjects(lecture.getConvertedAudioPath());
        }
        if (lecture.getConvertedPdfPath() != null) {
            s3StorageService.deleteObjects(lecture.getConvertedPdfPath());
        }
        // 만약 summarization 도 S3에 저장된 PDF/key라면:
        if (lecture.getSummarizationKey() != null && lecture.getSummarizationKey().startsWith("s3:")) {
            // summarization 필드에 "s3 키"를 그대로 넣었다면 조건 없이 추가해도 됨
            s3StorageService.deleteObject(lecture.getSummarizationKey());
        }

        // 3) DB 삭제
        lectureChunkRepository.deleteByLecture_Id(lectureId);
        audioChunkRepository.deleteByLecture_Id(lectureId);
        lectureRepository.deleteByIdAndUser_UserIdAndTitle(lectureId, userId, title);
    }

    public Lecture pollingLecture(Long lectureId, String userId) {
        return lectureRepository.findByIdAndUser_UserId(lectureId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Lecture not found or no permission"));
    }

    public List<LectureStatusResponse> findStatuses(List<Long> ids, String userId) {
        List<Lecture> lectures = lectureRepository.findByIdInAndUser_UserId(ids, userId);

        return lectures.stream()
                .map(l -> new LectureStatusResponse(
                        l.getId(),
                        l.getTitle(),
                        l.getStatus()
                ))
                .collect(Collectors.toList());
    }

    /* ----- 유틸 메서드들 ----- */
    private List<String> getExtension(MultipartFile [] files) {
        List<String> fileList = new ArrayList<>();
        for(MultipartFile file : files){
            String filename = file.getOriginalFilename();
            if (!StringUtils.hasText(filename) || !filename.contains(".")) {
                throw new IllegalArgumentException("파일 이름에 확장자가 없습니다: " + filename);
            }
            fileList.add(StringUtils.getFilenameExtension(filename).toLowerCase());
        }
        return fileList;
    }

    private List<String> getExtension(List<UploadFileData> files) {
        List<String> list = new ArrayList<>();
        for (UploadFileData f : files) {
            String filename = f.originalFilename();
            if (filename == null || !filename.contains(".")) {
                throw new IllegalArgumentException("파일 이름에 확장자가 없습니다: " + filename);
            }
            String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
            list.add(ext);
        }
        return list;
    }


}
