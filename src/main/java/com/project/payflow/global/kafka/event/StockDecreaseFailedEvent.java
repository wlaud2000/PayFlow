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
public class StockDecreaseFailedEvent extends DomainEvent {

    private static final String EVENT_TYPE = "STOCK_DECREASE_FAILED";
    private static final int SCHEMA_VERSION = 1;

    private Long paymentId;
    private Long productId;
    private int quantity;
    private String failureReason;  // String: Enum 미사용 — 서비스 분리 시 공유 의존성 제거

    private StockDecreaseFailedEvent(Long paymentId, Long productId, int quantity, String failureReason) {
        super(UUID.randomUUID().toString(), EVENT_TYPE, LocalDateTime.now(), SCHEMA_VERSION);
        this.paymentId = paymentId;
        this.productId = productId;
        this.quantity = quantity;
        this.failureReason = failureReason;
    }

    public static StockDecreaseFailedEvent of(Long paymentId, Long productId, int quantity, String failureReason) {
        return new StockDecreaseFailedEvent(paymentId, productId, quantity, failureReason);
    }
}