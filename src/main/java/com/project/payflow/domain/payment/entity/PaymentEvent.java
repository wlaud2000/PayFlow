package com.project.payflow.domain.payment.entity;

import com.project.payflow.domain.payment.enums.PaymentEventStatus;
import com.project.payflow.domain.payment.enums.PaymentEventType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "payment_event",
        uniqueConstraints = @UniqueConstraint(columnNames = "idempotency_key")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private PaymentEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentEventStatus status;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public PaymentEvent(Payment payment, String idempotencyKey, PaymentEventType eventType) {
        this.payment = payment;
        this.idempotencyKey = idempotencyKey;
        this.eventType = eventType;
        this.status = PaymentEventStatus.PENDING;
    }

    public static PaymentEvent pending(Payment payment, String idempotencyKey, PaymentEventType eventType) {
        return PaymentEvent.builder()
                .payment(payment)
                .idempotencyKey(idempotencyKey)
                .eventType(eventType)
                .build();
    }

    public void complete() {
        this.status = PaymentEventStatus.COMPLETED;
    }

    public void fail() {
        this.status = PaymentEventStatus.FAILED;
    }
}