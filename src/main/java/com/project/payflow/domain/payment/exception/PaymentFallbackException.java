package com.project.payflow.domain.payment.exception;

import com.project.payflow.domain.payment.enums.FailureReason;
import lombok.Getter;

// 인프라 레이어(TossPaymentsClient)에서 서비스 레이어(PaymentService)로 FailureReason을 전달하는 운반체
@Getter
public class PaymentFallbackException extends RuntimeException {

    private final FailureReason failureReason;

    public PaymentFallbackException(FailureReason failureReason, Throwable cause) {
        super(cause);
        this.failureReason = failureReason;
    }
}