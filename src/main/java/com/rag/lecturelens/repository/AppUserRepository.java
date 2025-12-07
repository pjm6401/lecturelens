package com.rag.lecturelens.repository;

import com.rag.lecturelens.domain.Provider;
import com.rag.lecturelens.entity.AppUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUserId(String email);

    Optional<AppUser> findByEmail(String email);

    boolean existsByUserId(String userId);

    boolean existsByEmail(String email);

    @Query("select u from AppUser u left join fetch u.lectures where u.userId = :userId")
    Optional<AppUser> findByUserIdWithLectures(@Param("userId") String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.userId = :userId")
    Optional<AppUser> findByUserIdForUpdate(@Param("userId") String userId);
    @Modifying
    @Query("UPDATE AppUser u SET u.usageLimit = u.dailyUsageLimit")
    void resetUsageLimit();
}

