package com.project.payflow.domain.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProductRequest(
        @NotBlank(message = "상품명을 입력해주세요")
        String name,

        @NotNull(message = "가격을 입력해주세요")
        @Positive(message = "가격은 0보다 커야 합니다")
        Long price,

        @NotNull(message = "재고를 입력해주세요")
        @Min(value = 0, message = "재고는 0 이상이어야 합니다")
        Integer stock
) {}