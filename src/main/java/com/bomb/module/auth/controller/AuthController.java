package com.bomb.module.auth.controller;

import com.bomb.common.result.Result;
import com.bomb.module.auth.dto.LoginRequest;
import com.bomb.module.auth.dto.RegisterRequest;
import com.bomb.module.auth.dto.TokenResponse;
import com.bomb.module.auth.service.AuthService;
import com.bomb.module.user.dto.UserVO;
import com.bomb.module.user.service.UserService;
import com.bomb.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/register")
    public Result<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @PostMapping("/login")
    public Result<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(userService.toVO(userService.getById(SecurityUtils.getCurrentUserId())));
    }
}
