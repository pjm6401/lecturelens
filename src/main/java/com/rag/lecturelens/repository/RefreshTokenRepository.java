package com.rag.lecturelens.repository;

import com.rag.lecturelens.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByUser_Id(Long userId);

    Optional<RefreshToken> findByToken(String token);

    void deleteByUser_Id(Long userId);
}

