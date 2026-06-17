package com.project.payflow.domain.order.service;

import com.project.payflow.domain.member.entity.Member;
import com.project.payflow.domain.member.exception.MemberErrorCode;
import com.project.payflow.domain.member.exception.MemberException;
import com.project.payflow.domain.member.repository.MemberRepository;
import com.project.payflow.domain.order.dto.CreateOrderRequest;
import com.project.payflow.domain.order.dto.OrderResponse;
import com.project.payflow.domain.order.entity.Order;
import com.project.payflow.domain.order.repository.OrderRepository;
import com.project.payflow.domain.product.entity.Product;
import com.project.payflow.domain.product.exception.ProductErrorCode;
import com.project.payflow.domain.product.exception.ProductException;
import com.project.payflow.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private OrderRepository orderRepository;
    private MemberRepository memberRepository;
    private ProductRepository productRepository;

    @Transactional
    public OrderResponse createOrder(Long memberId, CreateOrderRequest request) {
        // memberId는 JWT 인증값 — 요청 바디로 받지 않음 (위변조 방지)
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND));

        // soft check: 재고 차감은 결제 승인 시점(confirmPayment)에서 수행
        // 동시 요청에 대한 정합성은 여기서 보장하지 않음 -> 최종 방어는 @Version 낙관적 락
        if (product.getStock() < request.quantity()) {
            throw new ProductException(ProductErrorCode.INSUFFICIENT_STOCK);
        }

        Order order = Order.builder()
                .member(member)
                .product(product)
                .quantity(request.quantity())
                .build();

        return OrderResponse.from(orderRepository.save(order));
    }
}
