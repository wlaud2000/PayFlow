package com.project.payflow.domain.payment.dto;

import com.project.payflow.domain.payment.entity.Payment;

public record PaymentResponse(
        Long paymentId,
        Long orderId,
        Long amount,
        String status,
        String pgTransactionId
){
    public static PaymentResponse from(Payment payment){
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getAmount(),
                payment.getStatus().name(),
                payment.getPgTransactionId()
        );
    }
}