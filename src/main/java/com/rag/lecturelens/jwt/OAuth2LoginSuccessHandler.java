package com.rag.lecturelens.jwt;

import com.rag.lecturelens.entity.AppUser;
import com.rag.lecturelens.repository.AppUserRepository;
import com.rag.lecturelens.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final AppUserRepository appUserRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = (String) oAuth2User.getAttributes().get("email");

        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("OAuth2 로그인 유저를 찾을 수 없음"));

        String userId = user.getUserId();
        String role = user.getRole().name(); // ROLE_USER 등

        String accessToken = jwtTokenProvider.generateAccessToken(userId, role);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId);

        refreshTokenService.store(userId, refreshToken);

        String redirectUrl = "https://lecture-lens.com/oauth2/callback?accessToken=" + accessToken + "&refreshToken=" + refreshToken;
        response.sendRedirect(redirectUrl);

        /*
        // 🔹 1) JSON 으로 응답 (SPA, 앱용)
        response.setContentType("application/json;charset=UTF-8");
        String body = """
                {
                  "accessToken": "%s",
                  "refreshToken": "%s"
                }
                """.formatted(accessToken, refreshToken);
        response.getWriter().write(body);
        */
        // 🔹 2) 또는 redirect (프론트에서 토큰 받기)
        // response.sendRedirect("https://frontend.example.com/oauth2/success?accessToken=" + accessToken + "&refreshToken=" + refreshToken);
    }
}

