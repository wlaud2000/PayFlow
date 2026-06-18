package com.project.payflow.domain.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentRequestDto(
        @NotNull(message = "주문 ID를 입력해주세요")
        Long orderId,

        @NotNull(message = "결제 금액을 입력해주세요")
        @Positive(message = "결제 금액은 0보다 커야 합니다")
        Long amount
){}