package com.rag.lecturelens.domain;

public record LectureUploadResponse(
        Long lectureId,
        String title,
        LectureStatus status,
        int remainingUsage // 남은 사용 가능 횟수 (usage_limit)
) {}