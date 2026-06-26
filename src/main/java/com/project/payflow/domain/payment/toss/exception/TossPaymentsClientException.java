package com.project.payflow.domain.payment.toss.exception;

import com.project.payflow.domain.payment.toss.dto.TossErrorResponse;

// 4xx 응답 전반을 표현하는 클래스
public class TossPaymentsClientException extends TossPaymentsException {
    public TossPaymentsClientException(TossErrorResponse error) {
        super(error);
    }
}
