package com.rag.lecturelens.dto;

import com.rag.lecturelens.domain.LectureStatus;

public record LectureStatusResponse(
        Long lectureId,
        String title,
        LectureStatus status
) {}