package com.project.payflow.domain.payment.exception;

import com.project.payflow.global.apiPayload.exception.CustomException;

public class PaymentException extends CustomException{
    public PaymentException(PaymentErrorCode errorCode){
        super(errorCode);
    }
}