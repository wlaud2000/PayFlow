package com.project.payflow.domain.payment.service;

import com.project.payflow.domain.order.entity.Order;
import com.project.payflow.domain.order.enums.OrderStatus;
import com.project.payflow.domain.order.exception.OrderErrorCode;
import com.project.payflow.domain.order.exception.OrderException;
import com.project.payflow.domain.order.repository.OrderRepository;
import com.project.payflow.domain.payment.dto.*;
import com.project.payflow.domain.payment.entity.Payment;
import com.project.payflow.domain.payment.entity.PaymentEvent;
import com.project.payflow.domain.payment.enums.PaymentEventType;
import com.project.payflow.domain.payment.enums.PaymentStatus;
import com.project.payflow.domain.payment.exception.PaymentErrorCode;
import com.project.payflow.domain.payment.exception.PaymentException;
import com.project.payflow.domain.payment.exception.PaymentFallbackException;
import com.project.payflow.domain.payment.repository.PaymentEventRepository;
import com.project.payflow.domain.payment.repository.PaymentRepository;
import com.project.payflow.domain.payment.toss.TossPaymentsClient;
import com.project.payflow.domain.payment.toss.dto.TossCancelRequest;
import com.project.payflow.domain.payment.toss.dto.TossConfirmRequest;
import com.project.payflow.domain.payment.toss.dto.TossConfirmResponse;
import com.project.payflow.domain.product.service.ProductService;
import com.project.payflow.domain.product.service.ProductStockService;
import com.project.payflow.domain.product.service.RedisStockService;
import com.project.payflow.global.config.TossPaymentsProperties;
import com.project.payflow.global.kafka.event.PaymentApprovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final ProductStockService productStockService;
    private final ProductService productService;
    private final RedisStockService redisStockService;
    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final OrderRepository orderRepository;
    private final TossPaymentsClient tossPaymentsClient;
    private final TossPaymentsProperties tossPaymentsProperties;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public PaymentRequestResponse requestPayment(Long memberId, PaymentRequestDto request) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!order.getMember().getId().equals(memberId)) {
            throw new OrderException(OrderErrorCode.ORDER_FORBIDDEN);
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new OrderException(OrderErrorCode.INVALID_ORDER_STATUS);
        }

        long expectedAmount = order.getProduct().getPrice() * order.getQuantity();
        if (expectedAmount != request.amount()) {
            throw new PaymentException(PaymentErrorCode.AMOUNT_MISMATCH);
        }

        Payment payment = Payment.builder()
                .order(order)
                .amount(request.amount())
                .build();

        paymentRepository.save(payment);

        String idempotencyKey = order.getId() + ":" + PaymentEventType.CONFIRM.name();
        paymentEventRepository.save(PaymentEvent.pending(payment, idempotencyKey, PaymentEventType.CONFIRM));

        order.updateStatus(OrderStatus.PAYING);

        return PaymentRequestResponse.from(payment, order, request, tossPaymentsProperties);
    }

    /**
     * [Known Limitation]
     * Redis 차감 성공 후 DB 동기화 실패 시 Redis 재고가 실제보다 적게 표시될 수 있음.
     * StockSyncBatchService(10분 주기)가 이 불일치를 감지하고 로그로 알린다.
     * 결제 자체는 예외 처리로 실패 응답하여 사용자가 재시도하도록 유도.
     *
     * TossPayments API 성공 후 DB 저장 실패 시 결제 상태 불일치 발생 가능.
     * 현재는 @Transactional로 묶어서 단순 처리하되, Kafka 스프린트에서 Outbox 패턴으로 해결 예정.
     */
    @Transactional
    public PaymentResponse confirmPayment(ConfirmRequest request) {
        Long orderId = Long.valueOf(request.orderId());
        Payment payment = paymentRepository.findByOrder_Id(orderId)
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            return PaymentResponse.from(payment);
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new PaymentException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
        if (!payment.getAmount().equals(request.amount())) {
            throw new PaymentException(PaymentErrorCode.AMOUNT_MISMATCH);
        }

        String idempotencyKey = orderId + ":" + PaymentEventType.CONFIRM.name();
        PaymentEvent event = paymentEventRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_EVENT_NOT_FOUND));

        try {
            TossConfirmResponse tossConfirmResponse = tossPaymentsClient.confirm(
                    new TossConfirmRequest(request.paymentKey(), request.orderId(), request.amount())
            );
            payment.complete(tossConfirmResponse.paymentKey());
            payment.getOrder().updateStatus(OrderStatus.PAID);
            event.complete();
        } catch (PaymentFallbackException e) {
            payment.fail(e.getFailureReason());
            event.fail();
            throw new PaymentException(PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        }

        Long productId = payment.getOrder().getProduct().getId();
        int quantity = payment.getOrder().getQuantity();

        try {
            redisStockService.decreaseStock(productId, quantity);
        } catch (RedisConnectionFailureException e) {
            log.warn("[Redis 장애] 낙관적 락 경로로 전환 (productId={}). fallback 발생 빈도 모니터링 필요.", productId);
        }
        // REQUIRES_NEW + 재시도(3회, 50ms 지수 백오프)로 OptimisticLockException 처리
        productStockService.decreaseWithOptimisticLock(productId, quantity);

        // [Known Limitation] 과도기 상태 — 동기 처리와 이벤트 발행이 공존
        // 현재: 재고 차감을 동기로 처리하고, 이벤트도 발행 (중복 처리 가능성)
        // 개선: 다음 스프린트에서 Consumer가 재고 차감을 담당하면 여기서 제거 예정
        // 이 구조는 Kafka 없이도 동작 가능한 안전망 역할을 함
        applicationEventPublisher.publishEvent(PaymentApprovedEvent.of(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getOrder().getMember().getId(),
                productId,
                quantity,
                payment.getAmount(),
                payment.getPgTransactionId()
        ));

        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse cancelPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new PaymentException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }

        String idempotencyKey = payment.getOrder().getId() + ":" + PaymentEventType.CANCEL.name();
        PaymentEvent event = paymentEventRepository.save(
                PaymentEvent.pending(payment, idempotencyKey, PaymentEventType.CANCEL)
        );
        event.complete();

        payment.cancel();
        payment.getOrder().updateStatus(OrderStatus.CANCELLED);

        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse refundPayment(Long paymentId, RefundRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new PaymentException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }

        String idempotencyKey = payment.getOrder().getId() + ":" + PaymentEventType.REFUND.name();
        PaymentEvent event = paymentEventRepository.save(
                PaymentEvent.pending(payment, idempotencyKey, PaymentEventType.REFUND)
        );

        try {
            tossPaymentsClient.cancel(
                    payment.getPgTransactionId(),
                    new TossCancelRequest(request.cancelReason())
            );
        } catch (PaymentFallbackException e) {
            event.fail();
            throw new PaymentException(PaymentErrorCode.REFUND_GATEWAY_UNAVAILABLE);
        }

        event.complete();
        payment.refund();
        payment.getOrder().updateStatus(OrderStatus.REFUNDED);

        Long productId = payment.getOrder().getProduct().getId();
        int quantity = payment.getOrder().getQuantity();

        productStockService.increaseStock(productId, quantity);

        try {
            redisStockService.increaseStock(productId, quantity);
        } catch (RedisConnectionFailureException e) {
            log.warn("[Redis 장애] 환불 시 Redis 재고 복구 실패 (productId={}). DB 재고는 정상 복구됨.", productId);
        }

        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }
}
