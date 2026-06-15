package com.project.payflow.domain.product.dto;

import java.util.List;

public record ProductList(List<ProductResponse> items) {

    public static ProductList from(List<ProductResponse> items) {
        return new ProductList(items);
    }
}
