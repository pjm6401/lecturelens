package com.rag.lecturelens.repository;


import com.querydsl.jpa.impl.JPAQueryFactory;
import com.rag.lecturelens.entity.LectureChunk;
import com.rag.lecturelens.entity.QLectureChunk;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class LectureChunkRepositoryImpl implements LectureChunkRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final QLectureChunk chunk = QLectureChunk.lectureChunk;

    @Override
    public List<LectureChunk> findTopChunksByLectureId(Long lectureId, int limit) {
        return queryFactory
                .selectFrom(chunk)
                .where(chunk.lecture.id.eq(lectureId))
                .orderBy(chunk.importanceScore.desc())
                .limit(limit)
                .fetch();
    }
}
