package com.project.payflow.domain.payment.dto;

import com.project.payflow.domain.order.entity.Order;
import com.project.payflow.domain.payment.entity.Payment;
import com.project.payflow.global.config.TossPaymentsProperties;
import lombok.Builder;

@Builder
public record PaymentRequestResponse(
        Long paymentId,
        String orderId,
        Long amount,
        String clientKey
){
    public static PaymentRequestResponse from(
            Payment payment, Order order, PaymentRequestDto request, TossPaymentsProperties tossPaymentsProperties) {
        return PaymentRequestResponse.builder()
                .paymentId(payment.getId())
                .orderId(String.valueOf(order.getId()))
                .amount(request.amount())
                .clientKey(tossPaymentsProperties.getClientKey())
                .build();
    }
}
