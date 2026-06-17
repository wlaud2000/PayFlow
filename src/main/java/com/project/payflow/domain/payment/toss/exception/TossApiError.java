package com.project.payflow.domain.payment.toss.exception;

import com.project.payflow.domain.payment.toss.dto.TossErrorResponse;
import com.project.payflow.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

// Toss 에러 코드와 메시지는 동적 값이므로 enum으로 표현 불가 -> BaseErrorCode를 구현하는 일반 클래스로 작성
@Getter
@RequiredArgsConstructor
public class TossApiError implements BaseErrorCode {
    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    public static TossApiError from(TossErrorResponse error){
        return new TossApiError(HttpStatus.BAD_GATEWAY, error.code(), error.message());
    }
}
