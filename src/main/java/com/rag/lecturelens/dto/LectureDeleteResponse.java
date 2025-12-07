package com.rag.lecturelens.dto;

public record LectureDeleteResponse(
        String id,
        Long lectureId,
        String title
) {
}
