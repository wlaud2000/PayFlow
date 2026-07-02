package com.project.payflow.domain.payment.event;

import com.project.payflow.global.kafka.event.PaymentApprovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private static final String TOPIC = "payment-approved";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // @TransactionalEventListener vs @EventListener 차이:
    // @EventListener: 이벤트 발행 즉시 실행 — 트랜잭션 롤백 시에도 실행될 수 있어 "롤백됐는데 이벤트는 발행됨" 문제 발생
    // @TransactionalEventListener(AFTER_COMMIT): 트랜잭션 커밋 성공 후에만 실행
    // → TossPayments 실패 → 트랜잭션 롤백 → 이 메서드 호출되지 않음 (이벤트 미발행 보장)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(PaymentApprovedEvent event) {
        // 메시지 키로 paymentId 사용 이유:
        // 같은 결제에 대한 이벤트가 항상 같은 파티션으로 라우팅됨
        // → 파티션 내 순서 보장: 재처리 시 동일 결제 이벤트가 순서대로 처리됨
        // → null 키 사용 시 라운드로빈으로 파티션 결정 → 순서 보장 불가
        kafkaTemplate.send(TOPIC, event.getPaymentId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        // [Known Limitation] Kafka 발행 실패 시 이벤트 유실 가능
                        // 현재: 로그만 남기고 넘어감 (결제는 이미 완료된 상태)
                        // 개선: Outbox 패턴 도입으로 발행 보장 가능
                        // 참고: 결제 완료 → Kafka 발행 실패 → Consumer 미실행 → 재고 미차감 불일치
                        log.error("[PaymentEventPublisher] 이벤트 발행 실패 - eventId={}", event.getEventId(), ex);
                    } else {
                        log.info("[PaymentEventPublisher] 이벤트 발행 성공 - eventId={}, topic={}",
                                event.getEventId(), result.getRecordMetadata().topic());
                    }
                });
    }
}
