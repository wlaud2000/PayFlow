package com.project.payflow.domain.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
        @NotNull(message = "상품 ID를 입력해주세요")
        Long productId,

        @NotNull(message = "수량을 입력해주세요")
        @Min(value = 1, message = "수량은 1개 이상이어야 합니다")
        Integer quantity
){}