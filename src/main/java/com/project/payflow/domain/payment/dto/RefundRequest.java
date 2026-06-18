package com.project.payflow.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record RefundRequest(
        @NotBlank(message = "환불 사유를 입력해주세요")
        String cancelReason
){}