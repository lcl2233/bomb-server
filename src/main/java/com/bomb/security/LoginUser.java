package com.bomb.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginUser {

    private Long userId;
    private String username;
    private String role;
}
