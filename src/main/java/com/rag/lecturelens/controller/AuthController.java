package com.rag.lecturelens.controller;

import com.rag.lecturelens.domain.LectureStatus;
import com.rag.lecturelens.domain.PlanType;
import com.rag.lecturelens.dto.LoginRequest;
import com.rag.lecturelens.dto.SignupRequest;
import com.rag.lecturelens.entity.AppUser;
import com.rag.lecturelens.entity.Lecture;
import com.rag.lecturelens.jwt.JwtTokenProvider;
import com.rag.lecturelens.service.RefreshTokenService;
import com.rag.lecturelens.service.UserService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;


@RestController
@Slf4j
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService; // DB/Redis에 refresh token 저장용
    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(request.getUserId(), request.getPassword());

        log.info("로그인 요청 userId={}", request.getUserId());

        try {
            Authentication authentication = authenticationManager.authenticate(authToken); // 인증


            String userId = authentication.getName();
            String role = authentication.getAuthorities().iterator().next().getAuthority();
            AppUser user = userService.findUser(userId);
            String accessToken = jwtTokenProvider.generateAccessToken(userId, role);
            String refreshToken = jwtTokenProvider.generateRefreshToken(userId);

            refreshTokenService.store(userId, refreshToken);
            List<LectureSummaryDto> lectureDtos = user.getLectures().stream()
                    .sorted(Comparator.comparing(Lecture::getId).reversed())
                    .map(l -> new LectureSummaryDto(
                            l.getId(),
                            l.getTitle(),
                            l.getStatus()
                    ))
                    .toList();

            return ResponseEntity.ok(
                    new LoginResponse(
                            accessToken,
                            refreshToken,
                            new UserInfoDto(
                                    user.getUserId(),
                                    user.getEmail(),
                                    user.getPlanType(),   // 요금제
                                    user.getUsageLimit(), // 사용횟수
                                    user.getDailyUsageLimit(),
                                    lectureDtos
                            )
                    )
            );

        } catch (Exception e) {
            // AuthenticationException 계열 전부 포함됨
            log.warn("❌ 로그인 실패 userId={} - reason={}", request.getUserId(), e.getMessage());
            return ResponseEntity.status(401).body("Invalid credentials");
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest request) {
        log.info("회원가입 요청 userId={}, email={}", request.getUserId(), request.getEmail());

        try {
            AppUser user = userService.register(request);
            return ResponseEntity.status(201).body("회원가입 완료: " + user.getUserId());
        } catch (IllegalArgumentException e) {
            log.warn("회원가입 실패 - {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestBody RefreshRequest request) {

        String refreshToken = request.refreshToken();

        Claims claims = jwtTokenProvider.parseClaims(refreshToken);

        // 타입 체크
        if (!"refresh".equals(claims.get("type"))) {
            throw new RuntimeException("잘못된 토큰 타입");
        }

        String userId = claims.getSubject();

        // 저장된 refresh 와 일치하는지 확인 (로그아웃/탈취 방지)
        refreshTokenService.validate(userId, refreshToken);

        String role = "ROLE_USER"; // 필요하면 DB에서 다시 조회
        String newAccess = jwtTokenProvider.generateAccessToken(userId, role);
        String newRefresh = jwtTokenProvider.generateRefreshToken(userId);

        // refresh rotation: 기존 것 폐기 + 새 것 저장
        refreshTokenService.store(userId, newRefresh);

        return ResponseEntity.ok(new TokenResponse(newAccess, newRefresh));
    }

    record RefreshRequest(String refreshToken) {}

}


record TokenResponse(String accessToken, String refreshToken) {}

record LectureSummaryDto(
        Long id,
        String title,
        LectureStatus status
) {}

record UserInfoDto(
        String userId,
        String email,
        PlanType plan,
        int usageCount,
        int usageLimit,
        List<LectureSummaryDto> lectureList
) {}

record LoginResponse(
        String accessToken,
        String refreshToken,
        UserInfoDto user
) {}
