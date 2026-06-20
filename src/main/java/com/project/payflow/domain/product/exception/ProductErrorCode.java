package com.project.payflow.domain.product.exception;

import com.project.payflow.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum ProductErrorCode implements BaseErrorCode {

    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT404_0", "존재하지 않는 상품입니다"),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "PRODUCT409_0", "재고가 부족합니다"),
    STOCK_DECREASE_FAILED(HttpStatus.CONFLICT, "PRODUCT409_1", "재고 차감에 실패했습니다"),
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}