package com.project.payflow.domain.auth.controller;

import com.project.payflow.domain.auth.dto.LoginRequest;
import com.project.payflow.domain.auth.dto.LoginResponse;
import com.project.payflow.domain.auth.dto.SignupRequest;
import com.project.payflow.domain.auth.service.AuthService;
import com.project.payflow.global.apiPayload.CustomResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController{

    private final AuthService authService;

    @PostMapping("/signup")
    public CustomResponse<Void> signup(@RequestBody @Valid SignupRequest request){
        authService.signup(request);
        return CustomResponse.<Void>onSuccess("회원가입이 완료되었습니다", null);
    }

    @PostMapping("/login")
    public CustomResponse<LoginResponse> login(@RequestBody @Valid LoginRequest request){
        return CustomResponse.onSuccess("로그인이 완료되었습니다", authService.login(request));
    }
}