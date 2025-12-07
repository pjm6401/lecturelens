package com.rag.lecturelens.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "lecture_chunk")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LectureChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // N:1 관계 (여러 청크가 하나의 Lecture에 속함)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lecture_id", nullable = false)
    private Lecture lecture;

    @Column(name = "slide_no")
    private Integer slideNo;

    @Column(name = "start_sec")
    private Integer startSec;

    @Column(name = "end_sec")
    private Integer endSec;

    @Column(name = "text", nullable = false, columnDefinition = "text")
    private String text;

    @Column(name = "importance_score", nullable = false)
    private Float importanceScore;

    /**
     * 벡터 타입 매핑 (DB: vector(1536))
     * - JPA에선 일단 String으로 들고 있고
     * - insert/update는 NativeQuery 또는 JdbcTemplate로 처리해도 됨.
     *   예: '[0.1, 0.2, ...]' 형태의 문자열을 vector로 캐스팅.
     */
    @Column(name = "embedding", columnDefinition = "text")
    private String embedding;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "user_id")
    private String userId;

    @PrePersist
    public void onCreate() {
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
