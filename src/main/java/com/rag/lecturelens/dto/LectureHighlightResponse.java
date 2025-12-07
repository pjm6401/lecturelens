package com.rag.lecturelens.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LectureHighlightResponse {
    private Long chunkId;
    private Integer slideNo;
    private Float importanceScore;
    private String text;
}
