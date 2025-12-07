package com.rag.lecturelens.service;

import com.rag.lecturelens.domain.Provider;
import com.rag.lecturelens.domain.UserStatus;
import com.rag.lecturelens.dto.SignupRequest;
import com.rag.lecturelens.entity.AppUser;
import com.rag.lecturelens.entity.Lecture;
import com.rag.lecturelens.exceptionHandler.UsageLimitExceededException;
import com.rag.lecturelens.repository.AppUserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AppUser register(SignupRequest request) {

        // 1) userId 중복 체크
        if (appUserRepository.existsByUserId(request.getUserId())) {
            throw new IllegalArgumentException("이미 사용중인 userId 입니다.");
        }

        // 2) email 중복 체크 (원하면 스킵해도 됨)
        if (appUserRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용중인 이메일입니다.");
        }

        // 3) 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // 4) AppUser 엔티티 생성
        AppUser user = AppUser.builder()
                .userId(request.getUserId())
                .email(request.getEmail())
                .name(request.getName())
                .password(encodedPassword)
                // 아래 두 줄은 네 AppUser 정의에 맞게 수정
                .provider(Provider.LOCAL)        // enum이면 Provider.LOCAL
                .role(UserStatus.USER)             // enum이면 Role.USER
                .build();

        // 5) 저장
        return appUserRepository.save(user);
    }

    public AppUser findUser(String userId){
        return appUserRepository.findByUserIdWithLectures(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));
    }

    @Transactional
    public AppUser consumeUsage(String userId) {
        AppUser user = appUserRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (user.getUsageLimit() <= 0) {
            // 오늘 사용 횟수 다 씀
            throw new UsageLimitExceededException("오늘 사용 가능 횟수를 모두 사용했습니다.");
        }

        user.setUsageLimit(user.getUsageLimit() - 1);
        return user; // 변경 감지로 자동 update
    }
}
