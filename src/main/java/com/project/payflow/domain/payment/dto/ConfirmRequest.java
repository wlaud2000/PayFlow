package com.project.payflow.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ConfirmRequest(
        @NotBlank(message = "결제 키를 입력해주세요")
        String paymentKey,

        @NotBlank(message = "주문 ID를 입력해주세요")
        String orderId, // TossPayments가 String으로 처리

        @NotNull(message = "결제 금액을 입력해주세요")
        @Positive(message = "결제 금액은 0보다 커야 합니다")
        Long amount
){}
