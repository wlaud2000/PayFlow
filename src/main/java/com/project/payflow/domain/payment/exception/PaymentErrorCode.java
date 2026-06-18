package com.project.payflow.domain.payment.exception;

import com.project.payflow.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum PaymentErrorCode implements BaseErrorCode{

    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT404_0", "존재하지 않는 결제입니다"),
    PAYMENT_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT404_1", "결제 이벤트를 찾을 수 없습니다"),
    INVALID_PAYMENT_STATUS(HttpStatus.CONFLICT, "PAYMENT409_0", "현재 결제 상태에서 처리할 수 없는 요청입니다"),
    AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "PAYMENT400_0", "결제 금액이 주문 금액과 일치하지 않습니다"),
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}