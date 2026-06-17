package com.project.payflow.domain.payment.toss.exception;

import com.project.payflow.domain.payment.toss.dto.TossErrorResponse;
import com.project.payflow.global.apiPayload.exception.CustomException;

public class TossPaymentsException extends CustomException{
    public TossPaymentsException(TossErrorResponse error){
        super(TossApiError.from(error));
    }
}