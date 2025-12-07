package com.rag.lecturelens.service;

import com.rag.lecturelens.dto.UploadFileData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


@Service
@Slf4j
public class ConvertService {

    public List<byte[]> convertToMp4(MultipartFile[] files) {
        List<byte[]> convertList = new java.util.ArrayList<>();
        for(MultipartFile file : files){
            try {
                // TODO: 임시 구현 - 실제로는 진짜 mp4 결과를 리턴해야 함
                convertList.add(file.getBytes());
            } catch (Exception e) {
                throw new RuntimeException("오디오/영상 → MP4 변환 실패 (스텁)", e);
            }
        }
        return convertList;
    }

    public List<byte[]> convertToPdf(MultipartFile [] files) {
        List<byte[]> covertList = new ArrayList<>();
        for(MultipartFile file: files){
            try {
                // TODO: 임시 구현 - 실제로는 진짜 PDF 결과를 리턴해야 함
                // 지금은 MVP이므로 "원본을 그대로 pdf라고 가정" (파이프라인 테스트용)
                covertList.add(file.getBytes());
            } catch (Exception e) {
                throw new RuntimeException("문서 → PDF 변환 실패 (스텁)", e);
            }
        }
        return covertList;
    }
    /**
     * 📄 문서 → PDF 변환 (UploadFileData 기반)
     * - 예: ppt, pptx, doc, docx 등을 pdf byte[] 리스트로 변환
     */
    public List<byte[]> convertToPdfFromBytes(List<UploadFileData> docs) {
        List<byte[]> result = new ArrayList<>();

        for (UploadFileData doc : docs) {
            String filename = doc.originalFilename();
            String contentType = doc.contentType();
            byte[] bytes = doc.bytes();

            log.info("convertToPdfFromBytes - filename={}, contentType={}", filename, contentType);

            // 여기서 bytes → pdfBytes 변환
            // 예시로 InputStream을 만들어 라이브러리에 전달하는 경우:
            try (InputStream is = new ByteArrayInputStream(bytes)) {

                // TODO: 실제 변환 로직 연결
                // 예시:
                // byte[] pdfBytes = somePdfConverter.convert(is, filename, contentType);
                // 지금은 일단 그대로 넣어두고, 나중에 실제 변환기 붙이면 됨.

                byte[] pdfBytes = bytes; // 👈 임시: 변환 없이 그대로 사용 (MVP 테스트용)

                result.add(pdfBytes);

            } catch (Exception e) {
                log.error("문서 → PDF 변환 실패: filename={}", filename, e);
                throw new RuntimeException("문서 → PDF 변환 실패: " + filename, e);
            }
        }

        return result;
    }

    /**
     * 🎧 오디오 → MP4 변환 (UploadFileData 기반)
     * - 예: mp3, wav 등을 mp4 byte[] 리스트로 변환
     */
    public List<byte[]> convertToMp4FromBytes(List<UploadFileData> audios) {
        List<byte[]> result = new ArrayList<>();

        for (UploadFileData audio : audios) {
            String filename = audio.originalFilename();
            String contentType = audio.contentType();
            byte[] bytes = audio.bytes();

            log.info("convertToMp4FromBytes - filename={}, contentType={}", filename, contentType);

            try (InputStream is = new ByteArrayInputStream(bytes)) {

                // TODO: 실제 오디오 → mp4 변환 로직 연결
                // 예시:
                // byte[] mp4Bytes = someAudioConverter.convertToMp4(is, filename, contentType);
                // 지금은 일단 그대로 리턴

                byte[] mp4Bytes = bytes; // 👈 임시: 변환 없이 그대로 사용

                result.add(mp4Bytes);

            } catch (Exception e) {
                log.error("오디오 → MP4 변환 실패: filename={}", filename, e);
                throw new RuntimeException("오디오 → MP4 변환 실패: " + filename, e);
            }
        }

        return result;
    }
}
