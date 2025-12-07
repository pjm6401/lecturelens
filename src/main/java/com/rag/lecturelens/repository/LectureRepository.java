package com.rag.lecturelens.repository;

import com.rag.lecturelens.entity.Lecture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LectureRepository extends JpaRepository<Lecture, Long> {

    // LectureRepository
    Optional<Lecture> findByIdAndUser_UserIdAndTitle(Long id, String userId, String title);

    long deleteByIdAndUser_UserIdAndTitle(Long id, String userId, String title);

    Optional<Lecture> findByIdAndUser_UserId(Long lectureId, String userId);
    List<Lecture> findByIdInAndUser_UserId(List<Long> ids, String userId);
}
