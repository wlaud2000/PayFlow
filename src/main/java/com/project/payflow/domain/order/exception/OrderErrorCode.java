package com.project.payflow.domain.order.exception;

import com.project.payflow.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum OrderErrorCode implements BaseErrorCode{

    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER404_0", "존재하지 않는 주문입니다"),
    ORDER_FORBIDDEN(HttpStatus.FORBIDDEN, "ORDER403_0", "본인의 주문만 처리할 수 있습니다"),
    INVALID_ORDER_STATUS(HttpStatus.CONFLICT, "ORDER409_0", "현재 주문 상태에서 처리할 수 없는 요청입니다"),
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}