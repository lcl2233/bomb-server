package com.bomb.module.auth.service;

import com.bomb.module.auth.dto.LoginRequest;
import com.bomb.module.auth.dto.RegisterRequest;
import com.bomb.module.auth.dto.TokenResponse;
import com.bomb.module.user.entity.User;
import com.bomb.module.user.service.UserService;
import com.bomb.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    public TokenResponse register(RegisterRequest request) {
        User user = userService.register(request.getUsername(), request.getPassword(), request.getEmail());
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        return TokenResponse.builder()
                .token(token)
                .user(userService.toVO(user))
                .build();
    }

    public TokenResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        User user = userService.getByUsername(request.getUsername());
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        return TokenResponse.builder()
                .token(token)
                .user(userService.toVO(user))
                .build();
    }
}
