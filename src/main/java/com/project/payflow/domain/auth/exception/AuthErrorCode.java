package com.project.payflow.domain.auth.exception;

import com.project.payflow.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum AuthErrorCode implements BaseErrorCode{

    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "AUTH401_0", "비밀번호가 일치하지 않습니다"),
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}