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
public class PointEarnedEvent extends DomainEvent {

    private static final String EVENT_TYPE = "POINT_EARNED";
    private static final int SCHEMA_VERSION = 1;

    private Long paymentId;
    private Long memberId;
    private Long amount;    // 포인트 적립 기준 결제 금액

    private PointEarnedEvent(Long paymentId, Long memberId, Long amount) {
        super(UUID.randomUUID().toString(), EVENT_TYPE, LocalDateTime.now(), SCHEMA_VERSION);
        this.paymentId = paymentId;
        this.memberId = memberId;
        this.amount = amount;
    }

    public static PointEarnedEvent of(Long paymentId, Long memberId, Long amount) {
        return new PointEarnedEvent(paymentId, memberId, amount);
    }
}