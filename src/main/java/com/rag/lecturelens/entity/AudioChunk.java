package com.rag.lecturelens.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "audio_chunk")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AudioChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 어떤 강의에 속한 오디오 청크인지 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lecture_id", nullable = false)
    private Lecture lecture;

    /** 오디오 구간 시작/끝 (초 단위) */
    @Column(name = "start_sec")
    private Integer startSec;

    @Column(name = "end_sec")
    private Integer endSec;

    /** 이 구간에서 STT로 얻은 텍스트 */
    @Column(columnDefinition = "text")
    private String text;

    /** 중요도 점수 (0.0 ~ 1.0 정도 스케일 가정) */
    @Column(name = "importance_score")
    private Float importanceScore;

    @Column(name = "user_id")
    private String userId;

    /**
     * (선택) 임베딩 벡터
     * 지금은 JPA 매핑에서 제외(@Transient)해서 pgvector 에러 방지
     * 나중에 정말 벡터 DB까지 쓸 때 다시 매핑하면 됨
     */
    @Column(name = "embedding", columnDefinition = "text")
    private String embedding;
}
