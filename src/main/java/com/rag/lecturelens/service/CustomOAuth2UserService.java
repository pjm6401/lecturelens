package com.rag.lecturelens.service;

import com.rag.lecturelens.domain.PlanType;
import com.rag.lecturelens.domain.Provider;
import com.rag.lecturelens.domain.UserStatus;
import com.rag.lecturelens.entity.AppUser;
import com.rag.lecturelens.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final AppUserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String provider = userRequest.getClientRegistration().getRegistrationId(); // google, kakao, naver
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email = extractEmail(extractProvider(provider), attributes);
        String name = extractName(extractProvider(provider), attributes);

        // 1. 이메일로 DB 조회
        AppUser user = userRepository.findByEmail(email)
                .orElseGet(() -> registerNewUser(email, name, provider));

        // 2. 기존 사용자면 이름/프로필 업데이트
        updateUser(user, name);

        Collection<? extends GrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority(user.getRole().name()));

        return new DefaultOAuth2User(authorities, attributes, "email");
    }

    private AppUser registerNewUser(String email, String name, String provider) {
        AppUser newUser = AppUser.builder()
                .email(email)
                .userId(email)     // 로컬 ID 대신 이메일을 userId로 사용
                .name(name)
                .provider(Provider.valueOf(provider.toUpperCase()))
                .role(UserStatus.USER)
                .dailyUsageLimit(3)
                .autoBilling(false)
                .planType(PlanType.FREE)
                .build();

        return userRepository.save(newUser);
    }

    private void updateUser(AppUser user, String name) {
        user.setName(name);
        userRepository.save(user);
    }

    private String extractEmail(Provider provider, Map<String, Object> attributes) {
        switch (provider) {
            case GOOGLE:
                return (String) attributes.get("email");
            case NAVER:
                return (String) ((Map) attributes.get("response")).get("email");
            case KAKAO:
                Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
                return (String) kakaoAccount.get("email");
            default:
                throw new IllegalArgumentException("지원하지 않는 provider: " + provider);
        }
    }

    private String extractName(Provider provider, Map<String, Object> attributes) {
        switch (provider) {
            case GOOGLE:
                return (String) attributes.get("name");
            case NAVER:
                return (String) ((Map) attributes.get("response")).get("name");
            case KAKAO:
                Map<String, Object> profile = (Map<String, Object>) ((Map) attributes.get("kakao_account")).get("profile");
                return (String) profile.get("nickname");
            default:
                return "Unknown";
        }
    }

    private Provider extractProvider(String provider) {
        return Provider.valueOf(provider.toUpperCase());
    }
}
