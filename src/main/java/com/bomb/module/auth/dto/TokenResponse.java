package com.bomb.module.auth.dto;

import com.bomb.module.user.dto.UserVO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenResponse {

    private String token;
    private UserVO user;
}
