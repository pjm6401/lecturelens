package com.rag.lecturelens.dto;

import com.rag.lecturelens.domain.LectureStatus;

public record LectureUploadResponse(
        Long lectureId,
        String title,
        int remainingUsage,
        LectureStatus status
) {
}
