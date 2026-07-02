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
public class PaymentApprovedEvent extends DomainEvent {

    private static final String EVENT_TYPE = "PAYMENT_APPROVED";
    private static final int SCHEMA_VERSION = 1;

    private Long paymentId;
    private Long orderId;
    private Long memberId;
    private Long productId;
    private int quantity;
    private Long amount;
    private String pgTransactionId;

    private PaymentApprovedEvent(Long paymentId, Long orderId, Long memberId,
                                 Long productId, int quantity, Long amount, String pgTransactionId) {
        super(UUID.randomUUID().toString(), EVENT_TYPE, LocalDateTime.now(), SCHEMA_VERSION);
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.memberId = memberId;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
        this.pgTransactionId = pgTransactionId;
    }

    public static PaymentApprovedEvent of(Long paymentId, Long orderId, Long memberId,
                                          Long productId, int quantity, Long amount, String pgTransactionId) {
        return new PaymentApprovedEvent(paymentId, orderId, memberId, productId, quantity, amount, pgTransactionId);
    }
}