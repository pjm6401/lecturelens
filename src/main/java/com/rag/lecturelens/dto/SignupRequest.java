package com.rag.lecturelens.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignupRequest {

    private String userId;   // 로그인 ID
    private String email;
    private String name;
    private String password;
}
