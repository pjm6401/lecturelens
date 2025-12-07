package com.rag.lecturelens.service;


import com.rag.lecturelens.entity.AppUser;
import com.rag.lecturelens.entity.RefreshToken;
import com.rag.lecturelens.jwt.JwtTokenProvider;
import com.rag.lecturelens.repository.AppUserRepository;
import com.rag.lecturelens.repository.RefreshTokenRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AppUserRepository appUserRepository;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 로그인/재발급 시 Refresh Token 저장 (화이트리스트 방식)
     * - userId: Authentication.getName() 에서 넘어온 로그인 아이디 (예: email)
     */
    @Transactional
    public void store(String userId, String refreshToken) {
        // 1) JWT 에서 만료 시간 뽑기
        Claims claims = jwtTokenProvider.parseClaims(refreshToken);
        OffsetDateTime expiredAt = claims.getExpiration()
                .toInstant()
                .atOffset(ZoneOffset.UTC);

        // 2) 로그인 아이디로 실제 AppUser 조회
        //    - 여기서는 userId = email 이라고 가정
        AppUser user = appUserRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 3) 기존 리프레시 토큰 삭제 (한 유저당 1개만 유지)
        refreshTokenRepository.deleteByUser_Id(user.getId());

        // 4) 새 리프레시 토큰 저장
        RefreshToken entity = RefreshToken.builder()
                .user(user)
                .token(refreshToken)
                .expiredAt(expiredAt)
                .build();

        refreshTokenRepository.save(entity);
    }

    /**
     * 클라이언트가 준 refreshToken 이
     * - DB에 저장된 것과 동일한지
     * - 만료되지 않았는지
     * 검증
     */
    @Transactional(readOnly = true)
    public void validate(String userId, String refreshToken) {
        // userId 로 유저 조회
        AppUser user = appUserRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        RefreshToken saved = refreshTokenRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new IllegalStateException("저장된 리프레시 토큰이 없습니다. 다시 로그인 해주세요."));

        // 1) 토큰 문자열이 같은지 확인
        if (!saved.getToken().equals(refreshToken)) {
            throw new IllegalArgumentException("리프레시 토큰이 일치하지 않습니다. (탈취/로그아웃 등)");
        }

        // 2) 만료 여부 확인 (DB 기준)
        if (saved.getExpiredAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new IllegalStateException("리프레시 토큰이 만료되었습니다. 다시 로그인 해주세요.");
        }

        // 3) JWT 자체의 exp도 검사 (parse 과정에서 에러 터지면 예외 발생)
        jwtTokenProvider.parseClaims(refreshToken);
    }

    /**
     * 로그아웃 시 리프레시 토큰 제거용 (선택)
     */
    @Transactional
    public void deleteByUserId(String userId) {
        AppUser user = appUserRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        refreshTokenRepository.deleteByUser_Id(user.getId());
    }
}

