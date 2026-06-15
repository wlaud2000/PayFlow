package com.project.payflow.domain.member.exception;

import com.project.payflow.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum MemberErrorCode implements BaseErrorCode{

    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER404_0", "존재하지 않는 회원입니다"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "MEMBER409_0", "이미 사용 중인 이메일입니다"),
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}