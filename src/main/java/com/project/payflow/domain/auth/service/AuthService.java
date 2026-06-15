package com.project.payflow.domain.auth.service;

import com.project.payflow.domain.auth.dto.LoginRequest;
import com.project.payflow.domain.auth.dto.LoginResponse;
import com.project.payflow.domain.auth.dto.SignupRequest;
import com.project.payflow.domain.auth.exception.AuthErrorCode;
import com.project.payflow.domain.auth.exception.AuthException;
import com.project.payflow.domain.member.entity.Member;
import com.project.payflow.domain.member.exception.MemberErrorCode;
import com.project.payflow.domain.member.exception.MemberException;
import com.project.payflow.domain.member.repository.MemberRepository;
import com.project.payflow.global.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public void signup(SignupRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new MemberException(MemberErrorCode.EMAIL_ALREADY_EXISTS);
        }

        memberRepository.save(
                Member.builder()
                        .email(request.email())
                        .password(passwordEncoder.encode(request.password()))
                        .name(request.name())
                        .build()
        );
    }

    public LoginResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new AuthException(AuthErrorCode.INVALID_PASSWORD);
        }

        return new LoginResponse(jwtUtil.generateToken(member.getId()));
    }

}
