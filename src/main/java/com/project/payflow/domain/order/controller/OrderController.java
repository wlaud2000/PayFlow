package com.project.payflow.domain.order.controller;

import com.project.payflow.domain.order.dto.CreateOrderRequest;
import com.project.payflow.domain.order.dto.OrderResponse;
import com.project.payflow.domain.order.service.OrderService;
import com.project.payflow.global.apiPayload.CustomResponse;
import com.project.payflow.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomResponse<OrderResponse> createOrder(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                     @RequestBody @Valid CreateOrderRequest request) {
        return CustomResponse.onSuccess(HttpStatus.CREATED, "주문이 생성되었습니다",
                orderService.createOrder(userDetails.getMember().getId(), request));
    }

}
