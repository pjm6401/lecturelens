package com.rag.lecturelens.service;

import com.rag.lecturelens.dto.UploadFileData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageService {

    private final S3Client s3Client;

    @Value("${app.s3.bucket}")
    private String bucket;

    @Value("${app.s3.base-dir}")
    private String baseDir;

    // ===================== 업로드 ======================

    public List<String> uploadOriginalFile(String userId,
                                           Long lectureId,
                                           String logical,
                                           MultipartFile [] files) {
        List<String> keys = new ArrayList<>();
        for(MultipartFile file : files) {
            String logicalName = logical + file.getOriginalFilename();
            String key = buildKey(userId,lectureId, logicalName);

            try {
                PutObjectRequest putReq = PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(file.getContentType())
                        .acl(ObjectCannedACL.PRIVATE)
                        .build();

                s3Client.putObject(
                        putReq,
                        RequestBody.fromInputStream(file.getInputStream(), file.getSize())
                );

                log.info("S3 업로드 완료 (multipart): s3://{}/{}", bucket, key);
                keys.add(key);

            } catch (IOException e) {
                throw new RuntimeException("S3 업로드 실패: s3://" + bucket + "/" + key, e);
            }
        }
        return keys;
    }

    public List<String> uploadOriginalBytes(String userId,
                                            Long lectureId,
                                            String logical,
                                            List<UploadFileData> files) {
        List<String> keys = new ArrayList<>();
        for (UploadFileData file : files) {
            String logicalName = logical + file.originalFilename();
            String key = buildKey(userId, lectureId, logicalName);

            PutObjectRequest putReq = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.contentType())
                    .acl(ObjectCannedACL.PRIVATE)
                    .build();

            s3Client.putObject(
                    putReq,
                    RequestBody.fromBytes(file.bytes())
            );

            log.info("S3 업로드 완료 (multipart bytes): s3://{}/{}", bucket, key);
            keys.add(key);
        }
        return keys;
    }

    public List<String> uploadConvertFile(String userId,
                                          Long lectureId,
                                          String logical,
                                          List<byte[]> bytes,
                                          String contentType) {
        List<String> keys = new ArrayList<>();
        int cnt = 0;
        for( byte[] byteArray : bytes ) {
            String logicalName = logical + "_" + cnt++;
            String key = buildKey(userId,lectureId, logicalName);

            PutObjectRequest putReq = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .acl(ObjectCannedACL.PRIVATE)
                    .build();

            s3Client.putObject(
                    putReq,
                    RequestBody.fromBytes(byteArray)
            );

            log.info("S3 업로드 완료 (bytes): s3://{}/{}", bucket, key);
            keys.add(key);
        }

        return keys;
    }

    public String uploadResultFile(String userId,
                                   Long lectureId,
                                   String title,
                                   String logical,
                                   byte[] bytes,
                                   String contentType) {

        String logicalName = title + "_" + logical;
        String key = buildKey(userId,lectureId, logicalName);

        PutObjectRequest putReq = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .acl(ObjectCannedACL.PRIVATE)
                .build();

        s3Client.putObject(
                putReq,
                RequestBody.fromBytes(bytes)
        );
        log.info("S3 업로드 완료 (result): s3://{}/{}", bucket, key);
        return key;
    }

    // ===================== 다운로드 ======================

    public String getObjectUrl(String key) {
        String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8);
        return "https://" + bucket + ".s3.amazonaws.com/" + encodedKey;
    }

    private String buildKey(String userId,Long lectureId, String logicalName) {
        return baseDir + "/" + userId + "/" + lectureId + "/" + logicalName;
    }

    public ResponseInputStream<GetObjectResponse> getObjectStream(String key) {
        GetObjectRequest getReq = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        return s3Client.getObject(getReq);
    }

    public byte[] downloadFile(String summarizeKey) {
        return getObjectBytes(summarizeKey);
    }

    public byte[] getObjectBytes(String key) {
        GetObjectRequest req = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        try (ResponseInputStream<GetObjectResponse> s3is = s3Client.getObject(req)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = s3is.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("S3 객체 읽기 실패: " + key, e);
        }
    }

    // ===================== 삭제 ======================

    /**
     * S3에서 단일 객체 삭제
     */
    public void deleteObject(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            DeleteObjectRequest delReq = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            s3Client.deleteObject(delReq);
            log.info("S3 객체 삭제 완료: s3://{}/{}", bucket, key);
        } catch (S3Exception e) {
            // 없는 키를 지우려고 해도 보통은 크게 문제 없으니 로그만 남김
            log.warn("S3 객체 삭제 실패: s3://{}/{} - {}", bucket, key, e.awsErrorDetails().errorMessage());
        }
    }

    /**
     * S3에서 여러 객체 한 번에 삭제
     */
    public void deleteObjects(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }

        List<ObjectIdentifier> toDelete = keys.stream()
                .filter(k -> k != null && !k.isBlank())
                .map(k -> ObjectIdentifier.builder().key(k).build())
                .toList();

        if (toDelete.isEmpty()) {
            return;
        }

        try {
            DeleteObjectsRequest req = DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(Delete.builder().objects(toDelete).build())
                    .build();

            DeleteObjectsResponse res = s3Client.deleteObjects(req);
            log.info("S3 다건 삭제 완료: {}개 삭제", res.deleted().size());
        } catch (S3Exception e) {
            log.warn("S3 다건 삭제 실패: {}", e.awsErrorDetails().errorMessage());
        }
    }
}
