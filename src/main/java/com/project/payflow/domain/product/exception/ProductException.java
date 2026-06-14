package com.project.payflow.domain.product.exception;

import com.project.payflow.global.apiPayload.exception.CustomException;

public class ProductException extends CustomException {
    public ProductException(ProductErrorCode errorCode) {
        super(errorCode);
    }
}
