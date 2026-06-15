package com.project.payflow.domain.product.dto;

import com.project.payflow.domain.product.entity.Product;

public record ProductResponse(
        Long id,
        String name,
        Long price,
        Integer stock
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStock()
        );
    }
}