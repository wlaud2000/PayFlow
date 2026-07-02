# Kafka 도입 근거 및 설계 분석

> 작성 시점: 4주차 구현(결제 승인 + Redis 재고 차감 + 낙관적 락 재시도) 완료 후  
> 목적: 현재 동기 처리 구조의 실제 문제를 코드 기반으로 문서화하고, Kafka 도입 근거를 수립한다.

---

## 1. 현재 동기 처리 구조의 문제

### 문제 1 — 동기 처리의 응답 지연

`PaymentService.confirmPayment()`는 하나의 `@Transactional` 메서드 안에서
아래 작업을 순서대로 처리한다.

```
사용자 요청 (HTTP POST /payments/confirm)
  → TossPayments API 호출 (외부 네트워크 I/O, ~100–500ms)
  → Redis Lua Script 재고 차감
  → DB 낙관적 락 재고 차감 (최대 3회 재시도 × 50/100ms 슬립)
→ 사용자에게 응답 반환
```

모든 처리가 완료된 후에야 응답을 반환한다.  
향후 포인트 적립 서비스가 추가되면 그 처리 시간까지 더해져 응답 지연은 선형으로 증가한다.

```java
// PaymentService.confirmPayment() — 실제 코드
TossConfirmResponse tossConfirmResponse = tossPaymentsClient.confirm(...); // (1) 외부 API
payment.complete(tossConfirmResponse.paymentKey());                         // (2) DB 업데이트
redisStockService.decreaseStock(productId, quantity);                       // (3) Redis
productStockService.decreaseWithOptimisticLock(productId, quantity);        // (4) DB 낙관적 락
return PaymentResponse.from(payment);                                       // (5) 응답 — (4) 완료 후
```

(1)에서 PG 승인을 받은 시점에 사용자 관점에서는 결제가 이미 완료됐다.  
(3), (4)는 사용자가 기다려야 할 이유가 없는 후처리 작업이다.

---

### 문제 2 — 부분 실패 시 롤백 범위

TossPayments API 호출 성공 후 DB 처리가 실패하면 `@Transactional`이 DB 변경을 롤백하지만,
이미 PG사에서 처리된 결제는 롤백되지 않는다.

```
TossPayments API 호출 성공  ← 결제 승인됨, 사용자 계좌에서 출금
  → payment.complete() 호출
  → DB 저장 실패 (ex: 커넥션 풀 고갈, 낙관적 락 3회 모두 실패)
  → @Transactional 롤백 → DB 변경 취소
  ↳ 문제: TossPayments에서는 결제가 이미 처리된 상태
           돈은 빠져나갔는데 주문은 PENDING, 재고는 그대로
```

이 불일치를 해소하려면 롤백 시 Toss 취소 API를 별도로 호출해야 한다.  
그 취소 API 호출도 실패할 수 있어 불일치가 이중으로 쌓일 수 있다.

코드에도 이 문제가 주석으로 명시돼 있다:

```java
// PaymentService.java — Known Limitation 주석
// TossPayments API 성공 후 DB 저장 실패 시 결제 상태 불일치 발생 가능.
// 현재는 @Transactional로 묶어서 단순 처리하되, Kafka 스프린트에서 Outbox 패턴으로 해결 예정.
```

---

### 문제 3 — 서비스 확장 시 경계 재설계 필요

재고 서비스·포인트 서비스가 별도 마이크로서비스로 분리되는 시나리오를 가정하면:

```
현재 구조 (동기 호출)
  PaymentService.confirmPayment()
    → ProductStockService.decreaseWithOptimisticLock()  ← 같은 JVM, 같은 트랜잭션
    → PointService.earnPoint()                          ← (미구현, 추가 시 여기에 들어감)

분리 후 문제
  PaymentService → HTTP 호출 → StockService (별도 서버)
  PaymentService → HTTP 호출 → PointService (별도 서버)
  → 분산 트랜잭션 문제 발생
  → 트랜잭션 경계를 처음부터 다시 설계해야 함
```

이벤트 기반으로 도메인 경계를 먼저 설계해두면,  
서비스 분리 시 컨슈머 코드만 해당 서비스로 이전하면 된다.

---

## 2. 대안 비교

### 2-1. HTTP 동기 호출 (현재 구조)

결제 서비스가 재고 서비스·포인트 서비스를 직접 HTTP로 호출하는 방식.

**장점**: 구현 단순, 즉시 성공/실패 확인 가능  
**단점**: 응답 지연 누적, 부분 실패 시 롤백 복잡, 호출 대상 서비스 장애가 결제 서비스까지 전파

---

### 2-2. Spring Events (`@TransactionalEventListener`)

Spring 내장 이벤트 메커니즘.  
`@TransactionalEventListener(phase = AFTER_COMMIT)`을 사용하면
결제 트랜잭션 커밋 후 재고 차감 이벤트를 비동기로 처리할 수 있다.

```
결제 트랜잭션 커밋
  → ApplicationEventPublisher.publishEvent(new StockDecreaseEvent(...))
  → @TransactionalEventListener(AFTER_COMMIT) 수신
  → StockDecreaseHandler.handle()  ← 커밋 후 별도 실행
```

**장점**: 별도 인프라 불필요, 트랜잭션 커밋 후 실행으로 "DB 실패 시 이벤트 미발행" 문제 단순화  
**단점**:
- 이벤트가 JVM 메모리에만 존재 → JVM 크래시 시 이벤트 유실
- 같은 JVM 안에서만 동작 → 서비스 분리 불가
- 이벤트 유실 방지를 위해 Outbox 패턴을 추가하면 결국 별도 스토리지가 필요

---

### 2-3. Kafka

이벤트를 Kafka 브로커에 영속화하고 컨슈머가 독립적으로 처리하는 방식.

**장점**:
- 이벤트 브로커 디스크에 저장 → JVM 크래시 후에도 재처리 가능
- 컨슈머가 독립 배포 → 서비스 분리 시 컨슈머만 이전
- 처리 속도 차이를 버퍼링 → 재고 서비스가 느려져도 결제 응답에 영향 없음

**단점**: 인프라 운영 복잡도 증가, 메시지 순서 보장 조건 복잡, 최종 일관성(Eventual Consistency)

---

### 비교 요약

| 비교 항목 | HTTP 동기 호출 | Spring Events | Kafka |
|-----------|:---:|:---:|:---:|
| 응답 지연 | 높음 | 낮음 | 낮음 |
| 이벤트 영속성 | N/A | 없음 (JVM 메모리) | 있음 (브로커 디스크) |
| JVM 크래시 내성 | N/A | 없음 | 있음 |
| 부분 실패 처리 | 복잡 | 보통 | Saga 보상 트랜잭션 |
| 서비스 분리 용이성 | 낮음 | 낮음 | 높음 |
| 구현 복잡도 | 낮음 | 중간 | 높음 |

---

## 3. 이 프로젝트에서 Kafka를 선택한 이유

### "단일 서비스인데 왜 Kafka를 썼냐" — 답변 구조

이 프로젝트는 현재 단일 Spring Boot 서비스다.  
그럼에도 Kafka를 도입한 이유는 다음 세 가지다.

#### 이유 1 — 결제 도메인에서 이벤트 유실은 비즈니스 손실과 직결

PG사에서 결제를 승인했지만 재고 차감 이벤트가 유실되면,  
실제로는 팔린 상품의 재고가 줄지 않아 이중 판매로 이어진다.  
Spring Events는 JVM 크래시 시 이벤트 유실을 막을 수 없다.  
Kafka 브로커는 이벤트를 디스크에 영속화하므로, 컨슈머 재시작 후 재처리가 가능하다.

#### 이유 2 — Spring Events로 이벤트 유실을 막으려면 결국 Outbox가 필요

Spring Events에 Outbox 패턴을 적용하면:
- DB 트랜잭션 안에 이벤트를 저장하고
- 폴링으로 읽어 이벤트를 발행하는 구조가 된다

이 시점에서 "이벤트 브로커 없는 Kafka 유사 구조"가 만들어진다.  
어차피 별도 릴레이 메커니즘이 필요하다면,  
신뢰성이 검증된 Kafka를 직접 사용하는 것이 유지보수 면에서 낫다.

#### 이유 3 — 서비스 분리를 대비한 경계 설계

현재는 단일 서비스이지만, 재고·포인트 도메인이 별도 서비스로 분리될 가능성을 열어둔 설계다.  
Kafka 이벤트로 도메인 경계를 먼저 명확히 해두면,  
서비스 분리 시 컨슈머 코드만 해당 서비스로 이전하면 된다.  
동기 호출 구조라면 분산 트랜잭션 설계를 처음부터 다시 해야 한다.

---

## 4. Kafka 도입 후 전체 이벤트 흐름

### 4-1. Topic 설계

| Topic | 발행 주체 | 수신 주체 | 의미 |
|-------|-----------|-----------|------|
| `payment-approved` | PaymentService (Outbox Relay) | StockDecrease Consumer | 결제 승인 완료, 재고 차감 요청 |
| `payment-stock-decreased` | StockDecrease Consumer | PointEarn Consumer | 재고 차감 완료, 포인트 적립 요청 |
| `payment-saga-compensate` | 각 Consumer (실패 시) | Saga Compensate Consumer | 처리 실패, 보상 트랜잭션 요청 |

---

### 4-2. Choreography Saga 선택 근거

**Choreography vs Orchestration**

| | Choreography | Orchestration |
|---|---|---|
| 흐름 제어 | 각 서비스가 이벤트에 반응해 스스로 처리 | 중앙 오케스트레이터가 각 서비스에 지시 |
| 결합도 | 이벤트 스키마 기반 느슨한 결합 | 오케스트레이터와 각 서비스 간 직접 의존 |
| 흐름 파악 | 이벤트 추적 필요, 전체 흐름 파악 어려움 | 오케스트레이터 코드 하나로 전체 흐름 파악 가능 |
| 서비스 추가 | 새 컨슈머를 추가하면 됨 | 오케스트레이터 수정 필요 |

이 프로젝트는 서비스 분리 대비를 목적으로 하므로 서비스 간 결합도를 낮추는 **Choreography**를 선택한다.  
오케스트레이터라는 별도 컴포넌트 없이 각 도메인이 독립적으로 동작한다.

---

### 4-3. 이벤트 흐름 (정상 경로)

```
[HTTP] 사용자 → POST /payments/confirm
  → PaymentService.confirmPayment()
      → TossPayments API 호출 (결제 승인)
      → payment.complete() + payment_outbox INSERT  ← 단일 DB 트랜잭션
  → 사용자에게 즉시 응답 반환 (HTTP 200)

[Outbox Relay]
  → payment_outbox PENDING 레코드 폴링 (10초 주기)
  → Kafka 발행: payment-approved { paymentId, orderId, productId, quantity, amount }

[Consumer 1] StockDecrease Consumer
  → payment-approved 수신
  → Redis Lua Script 재고 차감
  → DB 낙관적 락 재고 차감
  → 성공 → payment-stock-decreased 발행

[Consumer 2] PointEarn Consumer
  → payment-stock-decreased 수신
  → 포인트 적립 처리
  → 성공 → payment-completed 발행 (흐름 종료)
```

### 4-4. 이벤트 흐름 (실패 / 보상 경로)

```
[Consumer 1] StockDecrease Consumer
  → 재고 차감 실패 (재고 부족, DB 오류)
  → payment-saga-compensate 발행 { reason: STOCK_DECREASE_FAILED, paymentId }

[Consumer 2] PointEarn Consumer
  → 포인트 적립 실패
  → payment-saga-compensate 발행 { reason: POINT_EARN_FAILED, paymentId }

[Consumer 3] Saga Compensate Consumer
  → STOCK_DECREASE_FAILED 수신:
      → Toss 취소 API 호출
      → Payment 상태 CANCELLED
  → POINT_EARN_FAILED 수신:
      → DB 재고 복구 (increaseStock)
      → Redis 재고 복구 (increaseStock)
      → Toss 취소 API 호출
      → Payment 상태 CANCELLED
```

---

## 5. Outbox 패턴 설계 (다음 스프린트 구현 예정)

### 왜 Outbox가 필요한가

```
문제 시나리오 (Outbox 없을 때):
  TossPayments API 호출 성공
  payment.complete() DB 저장 성공
  → 여기서 JVM 크래시
  → Kafka 이벤트 미발행
  → 재고 차감 안 됨 → 이중 판매 가능성

해결 구조 (Outbox 패턴):
  payment.complete() + payment_outbox INSERT  ← 동일 DB 트랜잭션 (원자적)
  → 트랜잭션 커밋 성공 = Outbox 레코드 존재 보장
  → Outbox Relay가 주기적으로 폴링 후 Kafka 발행
  → 발행 성공 시 SENT 처리
  → JVM 크래시 후 재시작해도 PENDING 레코드를 재처리
```

### Outbox 테이블 스키마

```sql
CREATE TABLE payment_outbox (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_id   BIGINT       NOT NULL,              -- payment_id
    aggregate_type VARCHAR(50)  NOT NULL,              -- 'Payment'
    event_type     VARCHAR(100) NOT NULL,              -- 'PaymentApprovedEvent'
    payload        JSON         NOT NULL,              -- 이벤트 전체 내용
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING | SENT | FAILED
    created_at     DATETIME     NOT NULL,
    sent_at        DATETIME
);
```

### Outbox Relay 의사코드

```
@Scheduled(fixedDelay = 10_000)
fun relayOutboxEvents() {
    val events = outboxRepository.findByStatus(PENDING)
    events.forEach { event ->
        try {
            kafkaTemplate.send(topic(event.eventType), event.payload).get()
            event.markSent()
        } catch (e: Exception) {
            event.markFailed()
        }
    }
}
```

> **Sprint 6 구현 예정**: `PaymentOutbox` 엔티티, `OutboxRepository`, `OutboxRelayService`,
> `KafkaProducerConfig`, `StockDecreaseConsumer`, `PointEarnConsumer`, `SagaCompensateConsumer`
