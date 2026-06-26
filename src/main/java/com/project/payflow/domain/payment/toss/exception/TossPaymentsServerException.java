package com.project.payflow.domain.payment.toss.exception;

import com.project.payflow.domain.payment.toss.dto.TossErrorResponse;

// 5xx 응답을 표현
public class TossPaymentsServerException extends TossPaymentsException {
    public TossPaymentsServerException(TossErrorResponse error) {
        super(error);
    }
}
