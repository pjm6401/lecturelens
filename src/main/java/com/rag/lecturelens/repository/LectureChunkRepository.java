package com.rag.lecturelens.repository;

import com.rag.lecturelens.entity.Lecture;
import com.rag.lecturelens.entity.LectureChunk;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface LectureChunkRepository extends JpaRepository<LectureChunk, Long> {

    void deleteByLecture(Lecture lecture);
/*
    List<LectureChunk> findByLectureOrderByImportanceScoreDesc(Lecture lecture);

    @Query(value = """
        SELECT * FROM lecture_chunk
        WHERE lecture_id = :lectureId
        ORDER BY embedding <=> CAST(:queryVec AS vector) ASC
        LIMIT :topN
    """, nativeQuery = true)
    List<LectureChunk> searchTopN(
            @Param("lectureId") Long lectureId,
            @Param("queryVec") String queryVec,
            @Param("topN") int topN
    );*/

    //List<LectureChunk> findByLectureIdOrderByImportanceScoreDesc(Long lectureId);

    // ✅ 벡터 유사도 기반 Top N 검색
    @Query(value = """
        select *
        from lecture_chunk
        where lecture_id = :lectureId
        order by (embedding::vector) <-> cast(:queryVec as vector)
        limit :topN
        """, nativeQuery = true)
    List<LectureChunk> searchTopN(
            @Param("lectureId") Long lectureId,
            @Param("queryVec") String queryVec,
            @Param("topN") int topN
    );
    // 중요도 순으로 상위 N개 가져오기
    List<LectureChunk> findTop20ByLectureIdOrderByImportanceScoreDesc(Long lectureId);

    // 중요도 없으면 슬라이드 번호 기준으로도 하나 만들어두면 좋음
    List<LectureChunk> findTop20ByLectureIdOrderBySlideNoAsc(Long lectureId);

    void deleteByLecture_Id(Long lectureId);
    // 특정 Lecture ID에 해당하는 모든 청크 조회
    List<LectureChunk> findByLecture_Id(Long lectureId);

}
