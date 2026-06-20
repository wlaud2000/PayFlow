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
import com.project.payflow.domain.payment.repository.PaymentEventRepository;
import com.project.payflow.domain.payment.repository.PaymentRepository;
import com.project.payflow.domain.payment.toss.TossPaymentsClient;
import com.project.payflow.domain.payment.toss.dto.TossCancelRequest;
import com.project.payflow.domain.payment.toss.dto.TossConfirmRequest;
import com.project.payflow.domain.payment.toss.dto.TossConfirmResponse;
import com.project.payflow.domain.product.service.ProductStockService;
import com.project.payflow.global.config.TossPaymentsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final ProductStockService productStockService;
    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final OrderRepository orderRepository;
    private final TossPaymentsClient tossPaymentsClient;
    private final TossPaymentsProperties tossPaymentsProperties;

    @Transactional
    public PaymentRequestResponse requestPayment(Long memberId, PaymentRequestDto request) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!order.getMember().equals(memberId)) {
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

    // TossPayments API 성공 후 DB 저장 실패 시 결제 상태 불일치 발생 가능.
    // 현재는 @Transactional로 묶어서 단순 처리하되, Kafka 스프린트에서 Outbox 패턴으로 해결 예정
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

        TossConfirmResponse tossConfirmResponse = tossPaymentsClient.confirm(
                new TossConfirmRequest(request.paymentKey(), request.orderId(), request.amount())
        );

        payment.complete(tossConfirmResponse.paymentKey());
        payment.getOrder().updateStatus(OrderStatus.PAID);
        event.complete();
        productStockService.decreaseWithOptimisticLock(
                payment.getOrder().getProduct().getId(),
                payment.getOrder().getQuantity()
        );

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
    public PaymentResponse refundPayment(Long paymentId, RefundRequest request){
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new PaymentException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }

        String idempotencyKey = payment.getOrder().getId() + ":" + PaymentEventType.REFUND.name();
        PaymentEvent event = paymentEventRepository.save(
                PaymentEvent.pending(payment, idempotencyKey, PaymentEventType.REFUND)
        );

        tossPaymentsClient.cancel(
                payment.getPgTransactionId(),
                new TossCancelRequest(request.cancelReason())
        );

        event.complete();
        payment.refund();
        payment.getOrder().updateStatus(OrderStatus.REFUNDED);
        productStockService.increaseStock(
                payment.getOrder().getProduct().getId(),
                payment.getOrder().getQuantity()
        );

        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long paymentId){
        return paymentRepository.findById(paymentId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }
}
