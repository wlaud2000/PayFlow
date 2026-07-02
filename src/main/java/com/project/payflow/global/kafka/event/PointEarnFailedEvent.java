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
public class PointEarnFailedEvent extends DomainEvent {

    private static final String EVENT_TYPE = "POINT_EARN_FAILED";
    private static final int SCHEMA_VERSION = 1;

    private Long paymentId;
    private Long memberId;
    private Long amount;
    private String failureReason;

    private PointEarnFailedEvent(Long paymentId, Long memberId, Long amount, String failureReason) {
        super(UUID.randomUUID().toString(), EVENT_TYPE, LocalDateTime.now(), SCHEMA_VERSION);
        this.paymentId = paymentId;
        this.memberId = memberId;
        this.amount = amount;
        this.failureReason = failureReason;
    }

    public static PointEarnFailedEvent of(Long paymentId, Long memberId, Long amount, String failureReason) {
        return new PointEarnFailedEvent(paymentId, memberId, amount, failureReason);
    }
}