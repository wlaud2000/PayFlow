package com.project.payflow.global.kafka.event;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

// [설계 결정] self-contained 이벤트 원칙 적용
// Consumer가 이벤트 하나만으로 처리를 완료할 수 있어야 함.
// memberId를 포함하지 않으면 PointEarnConsumer가
// paymentId → Payment 테이블 조회 → memberId 추출 과정이 필요해짐.
// 이는 포인트 서비스가 결제 도메인에 의존하게 되는 결합도 문제를 만듦.
// 나중에 서비스 분리 시 각 서비스가 자신의 이벤트만 보고 처리 가능해야 함.
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class StockDecreasedEvent extends DomainEvent {

    private static final String EVENT_TYPE = "STOCK_DECREASED";
    private static final int SCHEMA_VERSION = 1;

    private Long paymentId;
    private Long orderId;
    private Long memberId;    // PointEarnConsumer가 결제 DB 조회 없이 포인트 적립하기 위해 포함
    private Long productId;
    private int quantity;
    private Long amount;      // 포인트 적립 기준 금액

    private StockDecreasedEvent(Long paymentId, Long orderId, Long memberId,
                                Long productId, int quantity, Long amount) {
        super(UUID.randomUUID().toString(), EVENT_TYPE, LocalDateTime.now(), SCHEMA_VERSION);
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.memberId = memberId;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
    }

    public static StockDecreasedEvent of(Long paymentId, Long orderId, Long memberId,
                                         Long productId, int quantity, Long amount) {
        return new StockDecreasedEvent(paymentId, orderId, memberId, productId, quantity, amount);
    }
}