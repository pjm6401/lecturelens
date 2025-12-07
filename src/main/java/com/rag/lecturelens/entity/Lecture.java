package com.rag.lecturelens.entity;

import com.rag.lecturelens.domain.LectureStatus;
import com.rag.lecturelens.util.StringListConverter;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Entity
@Table(name = "lecture")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lecture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false)
    private AppUser user;

    @Column(length = 255, nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Convert(converter = StringListConverter.class)
    @Column(name = "original_pdf_path", columnDefinition = "text")
    private List<String> originalPdfPath;

    @Convert(converter = StringListConverter.class)
    @Column(name = "original_audio_path", columnDefinition = "text")
    private List<String> originalAudioPath;

    @Convert(converter = StringListConverter.class)
    @Column(name = "convert_pdf_path", columnDefinition = "text")
    private List<String> convertedPdfPath;

    @Convert(converter = StringListConverter.class)
    @Column(name = "convert_audio_path", columnDefinition = "text")
    private List<String> convertedAudioPath;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LectureStatus status = LectureStatus.PROCESSING;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "summarization", columnDefinition = "text")
    private String summarizationKey;

    @PrePersist
    public void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void updateStatusFailed() {
        this.status = LectureStatus.FAILED;
    }

    public void updateStatusReady() {
        this.status = LectureStatus.READY;
    }
    public void updateStoragePaths(
            List<String> originalPdfPaths,
            List<String> originalAudioPaths,
            List<String> convertedPdfPaths,
            List<String> convertedAudioPaths
    ) {
        this.originalPdfPath = originalPdfPaths;
        this.originalAudioPath = originalAudioPaths;
        this.convertedPdfPath = convertedPdfPaths;
        this.convertedAudioPath = convertedAudioPaths;
    }

    public void setOriginalPdfPath(List<String> originalPdfPaths,List<String> originalAudioPaths) {
        this.originalPdfPath = originalPdfPaths;
        this.originalAudioPath = originalAudioPaths;
    }
}
