package com.rag.lecturelens.service;

import com.rag.lecturelens.entity.AppUser;
import com.rag.lecturelens.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {

        // ⚠️ 여기서 username = 로그인에 사용하는 userId (또는 email)
        // AppUser 엔티티에 맞춰서 findByUserId 또는 findByEmail로 바꿔 쓰면 됨
        AppUser user = appUserRepository.findByUserId(userId)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + userId));

        List<GrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority(user.getRole().toString())); // ROLE_USER 같은 값

        return new User(
                user.getUserId(),    // username
                user.getPassword(),  // 인코딩된 비밀번호 (BCrypt)
                authorities
        );
    }
}
