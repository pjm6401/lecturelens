package com.rag.lecturelens.repository;

import com.rag.lecturelens.entity.AudioChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AudioChunkRepository extends JpaRepository<AudioChunk, Long> {

    // 중요도 높은 순으로 상위 30개
    List<AudioChunk> findTop30ByLectureIdOrderByImportanceScoreDesc(Long lectureId);

    // 중요도 정보가 없다면, 시간 순으로 상위 30개
    List<AudioChunk> findTop30ByLectureIdOrderByStartSecAsc(Long lectureId);

    void deleteByLecture_Id(Long lectureId);

    List<AudioChunk> findByLecture_Id(Long lectureId);

}
