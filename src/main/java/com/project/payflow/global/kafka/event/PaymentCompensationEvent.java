package com.project.payflow.global.kafka.event;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class PaymentCompensationEvent extends DomainEvent {

    private static final String EVENT_TYPE = "PAYMENT_COMPENSATION";
    private static final int SCHEMA_VERSION = 1;

    // String 타입: Enum 미사용 — 서비스 분리 시 공유 의존성 제거
    // "STOCK_DECREASE_FAILED" 또는 "POINT_EARN_FAILED"
    private String compensationType;
    private Long paymentId;
    private Long productId;    // 재고 복구 시 필요
    private Long memberId;     // 보상 대상 회원 식별
    private int quantity;      // 재고 복구 수량
    private Long amount;       // 결제 취소 금액
    private String failureReason;

    private PaymentCompensationEvent(String compensationType, Long paymentId, Long productId,
                                     Long memberId, int quantity, Long amount, String failureReason) {
        super(UUID.randomUUID().toString(), EVENT_TYPE, LocalDateTime.now(), SCHEMA_VERSION);
        this.compensationType = compensationType;
        this.paymentId = paymentId;
        this.productId = productId;
        this.memberId = memberId;
        this.quantity = quantity;
        this.amount = amount;
        this.failureReason = failureReason;
    }

    public static PaymentCompensationEvent of(String compensationType, Long paymentId, Long productId,
                                              Long memberId, int quantity, Long amount, String failureReason) {
        return new PaymentCompensationEvent(compensationType, paymentId, productId,
                memberId, quantity, amount, failureReason);
    }
}