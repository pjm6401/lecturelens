package com.rag.lecturelens.controller;

import com.rag.lecturelens.domain.LectureStatus;
import com.rag.lecturelens.dto.*;
import com.rag.lecturelens.entity.AppUser;
import com.rag.lecturelens.entity.Lecture;
import com.rag.lecturelens.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


@RestController
@Slf4j
@RequestMapping("/api/lectures")
@RequiredArgsConstructor
public class LectureController {

    private final LectureService lectureService;
    private final UserService userService;
    /**
     * 강의 업로드 (문서 + 음성/영상)
     * - document: pdf / ppt / pptx / doc / docx
     * - audio: mp4 / mp3 / avi
     */

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LectureUploadResponse> uploadLecture(
            @RequestPart("userId") String userId,
            @RequestPart("title") String title,
            @RequestPart(value = "description", required = false) String description,
            @RequestPart("document") MultipartFile[] documentFiles,
            @RequestPart("audio") MultipartFile[] audioFiles,
            Authentication authentication // ✅ JWT 인증 정보
    ) throws IOException {

        // 1) 오늘 사용량 1회 차감
        AppUser user = userService.consumeUsage(userId);

        // 2) Lecture "껍데기" 먼저 생성 (status = PROCESSING)
        Lecture lecture = lectureService.createInitialLecture(user, title, description);

        List<UploadFileData> docData = new ArrayList<>();
        for (MultipartFile f : documentFiles) {
            docData.add(new UploadFileData(
                    f.getOriginalFilename(),
                    f.getContentType(),
                    f.getBytes()
            ));
        }

        List<UploadFileData> audioData = new ArrayList<>();
        for (MultipartFile f : audioFiles) {
            audioData.add(new UploadFileData(
                    f.getOriginalFilename(),
                    f.getContentType(),
                    f.getBytes()
            ));
        }

        // 3) 비동기 처리 시작 (lectureId 넘기기)
        lectureService.processLectureAsync(
                lecture.getId(),
                userId,
                docData,
                audioData
        );


        // 4) 프론트로 응답: 강의 id + title + 남은 사용횟수 + 초기 status
        LectureUploadResponse response = new LectureUploadResponse(
                lecture.getId(),
                lecture.getTitle(),
                user.getUsageLimit(),
                lecture.getStatus() // PROCESSING
        );
        log.info("return {}",response);
        return ResponseEntity.ok(response);
    }


    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadSummary(
            @RequestParam("userId") String userId,
            @RequestParam("lectureId") Long lectureId,
            @RequestParam("title") String title
    ) {

        DownloadFile downloadFile = lectureService.downloadS3File(userId,lectureId,title);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF); // 결과물이 PDF라면
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(downloadFile.getFileName(), StandardCharsets.UTF_8)
                        .build()
        );

        return new ResponseEntity<>(downloadFile.getFileBytes(), headers, HttpStatus.OK);
    }

    @DeleteMapping ("/delete")
    public ResponseEntity<LectureDeleteResponse> delete(
            @RequestParam("userId") String userId,
            @RequestParam("lectureId") Long lectureId,
            @RequestParam("title") String title
    ) {

        //delete file from s3, db
        lectureService.deleteLectureAndS3(userId,lectureId,title);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON); // 결과물이 PDF라면
        LectureDeleteResponse lectureDeleteResponse = new LectureDeleteResponse(
                userId,
                lectureId,
                title
        );

        return new ResponseEntity<>(lectureDeleteResponse, headers, HttpStatus.OK);
    }
    @GetMapping("/{lectureId}/status")
    public ResponseEntity<LectureStatusResponse> getLectureStatus(
            @PathVariable Long lectureId,
            Authentication authentication
    ) {
        String userId = authentication.getName();

        Lecture lecture = lectureService.pollingLecture(lectureId, userId);

        LectureStatusResponse response = new LectureStatusResponse(
                lecture.getId(),
                lecture.getTitle(),
                lecture.getStatus()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<List<LectureStatusResponse>> getLectureStatuses(
            @RequestParam List<Long> ids,
            Authentication authentication
    ) {
        String userId = authentication.getName();

        var dtos = lectureService.findStatuses(ids, userId);

        List<LectureStatusResponse> responses = dtos.stream()
                .map(d -> new LectureStatusResponse(
                        d.lectureId(),
                        d.title(),
                        d.status()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

}
