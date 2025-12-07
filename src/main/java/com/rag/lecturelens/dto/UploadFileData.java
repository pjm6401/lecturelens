// package 예시는 dto 쪽에 둬도 되고, service 패키지 안에 둬도 됨
package com.rag.lecturelens.dto;

public record UploadFileData(
        String originalFilename,
        String contentType,
        byte[] bytes
) {}
