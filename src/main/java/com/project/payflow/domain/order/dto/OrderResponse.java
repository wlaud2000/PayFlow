package com.project.payflow.domain.order.dto;

import com.project.payflow.domain.order.entity.Order;

public record OrderResponse(
        Long orderId,
        String productName,
        Integer quantity,
        Long totalAmount,
        String status
){
    public static OrderResponse from(Order order){
        return new OrderResponse(
                order.getId(),
                order.getProduct().getName(),
                order.getQuantity(),
                order.getProduct().getPrice() * order.getQuantity(),
                order.getStatus().name()
        );
    }
}