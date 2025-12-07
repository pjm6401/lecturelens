package com.rag.lecturelens.repository;

import com.rag.lecturelens.entity.LectureChunk;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface LectureChunkRepositoryCustom {

    /**
     * 중요도(importance_score)가 높은 상위 N개의 청크 조회
     */
    List<LectureChunk> findTopChunksByLectureId(Long lectureId, int limit);

}
