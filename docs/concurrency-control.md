# 동시성 제어 방식 비교 분석

## 배경

`PaymentService.confirmPayment()`에서 `Product.decreaseStock()`이 호출될 때,
동시에 여러 요청이 같은 상품의 재고를 차감하려 하면 데이터 정합성 문제가 발생한다.

현재 구현: `Product` 엔티티에 `@Version Long version` (JPA 낙관적 락) 적용.
이 문서는 4가지 동시성 제어 방식을 비교하고, 낙관적 락을 먼저 유지하되
실험을 통해 전환 필요성을 판단하는 근거를 만드는 과정을 담는다.

**이 프로젝트의 조건**

- 단일 인스턴스 Spring Boot (분산 락의 "분산" 이점 현재 불필요)
- MySQL + JPA 환경
- 한정 수량 상품 — 재고가 적을수록 동시 충돌 빈도가 높아짐
- `confirmPayment` 호출 시점에 재고 차감 (주문 생성이 아닌 결제 승인 시)

---

## 4가지 방식 비교

### 방식별 개요

#### 1. 비관적 락 (Pessimistic Lock / SELECT FOR UPDATE)

DB에서 특정 행에 배타적 락을 건다.
락을 잡은 트랜잭션이 완료될 때까지 다른 트랜잭션은 해당 행을 읽거나 수정할 수 없다.

```java
// Spring Data JPA
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Product> findById(Long id);
```

- **원자성 보장**: DB가 행 수준에서 직접 보장
- **충돌 처리**: 대기 후 순차 처리 (락이 해제될 때까지 블로킹)
- **단일 인스턴스에서**: DB 커넥션 점유 시간이 트랜잭션 전체 시간과 같음

#### 2. 낙관적 락 (Optimistic Lock / @Version)

데이터를 읽을 때 version 값을 함께 읽고, 업데이트 시 `WHERE id = ? AND version = ?`로
버전이 그 사이 변경되지 않았는지 확인한다. 변경됐으면 `OptimisticLockException` 발생.

```java
// Product.java
@Version
private Long version;

// 실행되는 SQL
UPDATE product SET stock = ?, version = ? + 1 WHERE id = ? AND version = ?
```

- **원자성 보장**: 단일 UPDATE SQL의 WHERE 조건으로 보장
- **충돌 처리**: 예외 발생 → 서비스 레이어에서 재시도 또는 실패 처리
- **재시도 비용**: 충돌 시 전체 `confirmPayment` 트랜잭션을 다시 실행

#### 3. Redis 분산 락

Redis를 중앙 락 저장소로 사용해 임계 구역 진입을 제어한다.
두 가지 구현 방식이 있다.

**Raw SETNX + TTL (직접 구현)**
```
SET lock:product:42 <uuid> NX EX 5
# 성공 시: 락 획득
# 실패 시: 이미 다른 인스턴스가 락 보유 중
```

**Redisson RLock (라이브러리)**
```java
RLock lock = redisson.getLock("lock:product:" + productId);
try {
    if (lock.tryLock(3, 5, TimeUnit.SECONDS)) {
        // 임계 구역
    }
} finally {
    lock.unlock();
}
```

Redisson은 Lock 갱신(watchdog), 재진입(reentrant), Pub/Sub 대기 등을 자동 처리한다.
Raw SETNX는 락 해제 누락(crash) 대비 TTL에만 의존한다.

- **원자성 보장**: Redis 단일 노드에서 SET NX는 원자적
- **충돌 처리**: 락 획득 실패 시 대기(`tryLock` waitTime) 또는 즉시 실패
- **단일 인스턴스에서**: 같은 프로세스 내 스레드 간 Redis 왕복이 추가 레이턴시

#### 4. Redis Lua Script

Redis에서 재고 조회 + 조건 검사 + 차감을 하나의 Lua 스크립트로 원자적으로 실행한다.
Redis는 단일 스레드로 동작하므로 Lua Script 실행 중에는 다른 명령이 끼어들 수 없다.

```lua
-- 재고 차감 Lua Script
local stock = redis.call('GET', KEYS[1])
if tonumber(stock) < tonumber(ARGV[1]) then
    return -1  -- 재고 부족
end
return redis.call('DECRBY', KEYS[1], ARGV[1])
```

- **원자성 보장**: Redis 단일 스레드 + Lua 실행의 불가분성
- **충돌 처리**: 충돌 개념 없음 — 요청이 순서대로 원자적 처리
- **재시도 불필요**: 하나의 스크립트가 검사와 차감을 동시에 수행

---

### 비교표

| 항목 | 비관적 락 | 낙관적 락 | Redis 분산 락 (Redisson) | Redis Lua Script |
|------|-----------|-----------|--------------------------|-----------------|
| **원자성 보장 방식** | DB 행 락 (SELECT FOR UPDATE) | UPDATE WHERE version = ? | Redis SETNX (원자적 SET) | Redis 단일 스레드 Lua 실행 |
| **충돌 처리 방식** | 대기 후 순차 처리 | OptimisticLockException → 재시도 | 락 대기 / 획득 실패 반환 | 충돌 없음 (원자적) |
| **성능 특성** | 동시성 높을수록 대기 누적, DB 커넥션 점유 | 충돌 적으면 빠름. 충돌 잦으면 재시도 폭풍 | 인메모리 빠름. 네트워크 왕복 1회 추가 | 가장 빠름 (네트워크 1회 + 재시도 없음) |
| **주요 한계** | 커넥션 풀 고갈 위험, 분산 환경 취약 | 재시도 연쇄 시 DB 부하 증가 | Redis 인프라 필요, watchdog 복잡성 | Redis 재고 ↔ DB 동기화 필요 |
| **추가 인프라** | 없음 | 없음 | Redis 필요 | Redis 필요 |
| **단일 인스턴스 적합성** | 보통 | 좋음 (충돌이 적다면) | 분산 이점 활용 불가 — 과함 | 좋음 (인프라 구축 후) |
| **분산 인스턴스 확장성** | 취약 | 취약 (재시도 폭풍 악화) | 적합 | 적합 (Redis 중앙화) |

---

## 낙관적 락을 먼저 선택한 이유

단순히 "쉬워서"가 아니라 이 시점의 프로젝트 조건에 가장 적합하기 때문이다.

1. **추가 인프라 없음**: 단일 인스턴스 환경에서 Redis를 도입하는 비용 대비 이득이 불명확하다.
   충돌이 실제로 얼마나 발생하는지 측정하기 전에 인프라를 선제 도입하는 것은 과잉 설계다.

2. **기준선(baseline) 확보**: 낙관적 락으로 먼저 운영해야 충돌률 데이터를 얻을 수 있다.
   측정값 없이 "Lua Script가 더 낫다"는 결론은 근거 없는 주장이다.

3. **@Version 하나로 충분할 수도 있다**: 한정 수량 상품이라도 요청이 균등하게 분산되면
   충돌률이 낮을 수 있다. 실험 결과가 낮은 충돌률을 보이면 낙관적 락이 최적이다.

4. **Lua Script 전환의 비용**: Redis 재고를 DB와 동기화하는 구조를 도입하면 복잡도가 증가한다.
   이 복잡도는 충돌률 데이터로 정당화되어야 한다.

---

## 실험 설계

### 목적

낙관적 락 환경에서 실제 충돌이 얼마나 발생하는지 측정해,
Lua Script 전환이 필요한지 판단하는 근거를 수치로 확보한다.

### 시나리오 설정

| 항목 | 값 | 선택 이유 |
|------|----|-----------|
| 상품 초기 재고 | 100 | 한정 수량 상품 가정 |
| 동시 요청 수 | 150 | 재고를 모두 소진한 뒤에도 50건의 추가 요청이 발생하는 시나리오. 충돌이 집중되는 구간을 의도적으로 생성 |
| 요청당 차감 수량 | 1 | 단순화. 수량 N으로 변경해 차감 집중도를 조절 가능 |

### 실험 코드 설계 (ExecutorService + CountDownLatch)

```java
int threadCount = 150;
AtomicInteger successCount = new AtomicInteger();
AtomicInteger conflictCount = new AtomicInteger();   // OptimisticLockException
AtomicInteger totalRetries = new AtomicInteger();

ExecutorService executor = Executors.newFixedThreadPool(threadCount);
CountDownLatch ready = new CountDownLatch(threadCount);
CountDownLatch start = new CountDownLatch(1);
CountDownLatch done = new CountDownLatch(threadCount);

for (int i = 0; i < threadCount; i++) {
    executor.submit(() -> {
        try {
            ready.countDown();
            start.await();

            int retries = 0;
            while (retries <= MAX_RETRY) {
                try {
                    paymentService.confirmPayment(...);
                    successCount.incrementAndGet();
                    break;
                } catch (OptimisticLockingFailureException e) {
                    conflictCount.incrementAndGet();
                    totalRetries.incrementAndGet();
                    retries++;
                    if (retries > MAX_RETRY) throw e;
                }
            }
        } finally {
            done.countDown();
        }
    });
}

ready.await();
start.countDown();
done.await(30, TimeUnit.SECONDS);
```

### 측정 지표

| 지표 | 설명 |
|------|------|
| `successCount` | 재고 차감 성공 건수 (최대 100이어야 정합성 유지) |
| `conflictCount` | `OptimisticLockingFailureException` 총 발생 횟수 |
| `conflictRate` | `conflictCount / threadCount * 100` (%) |
| `avgRetries` | `totalRetries / threadCount` (요청당 평균 재시도 횟수) |

### 실험 결과 → 전환 기준 도출

실험 결과를 아래 표에 채운 뒤, 수치를 보고 전환 여부를 판단한다.
**임계값을 사전에 정하지 않는다.** 동시 요청 수와 재고 비율에 따라 충돌률이 달라지므로
수치를 먼저 확보하고 기준을 도출하는 것이 데이터 기반 의사결정이다.

| 시나리오 | 성공 건수 | 충돌 횟수 | 충돌률 | 평균 재시도 | 판단 |
|----------|-----------|-----------|--------|------------|------|
| 재고 100 / 요청 150 | [미확인] | [미확인] | [미확인] | [미확인] | [미확인] |
| 재고 100 / 요청 300 | [미확인] | [미확인] | [미확인] | [미확인] | [미확인] |
| 재고 10 / 요청 150 | [미확인] | [미확인] | [미확인] | [미확인] | [미확인] |

---

## Lua Script 전환 시 고려사항 (이론)

충돌률 실험 결과가 낙관적 락의 재시도 비용을 정당화하지 못한다면 Lua Script로 전환한다.
전환 시 아래 사항을 추가로 설계해야 한다.

### Redis ↔ DB 재고 동기화 전략

Lua Script는 Redis에 저장된 재고 값을 원자적으로 차감한다.
DB의 `product.stock`은 별도로 존재하므로 두 값을 일치시키는 전략이 필요하다.

**옵션 A. 서버 시작 시 Redis 워밍업**
```
서버 기동 → Redis에 product.stock 값 로딩 → Lua Script로 Redis 재고 관리
→ 결제 완료 시 DB stock도 UPDATE (Redis 차감 + DB 차감 두 번)
```
- 장점: 빠르고 단순
- 단점: Redis 재시작 시 재로딩 필요. Redis와 DB 동시 업데이트의 원자성 보장 불가

**옵션 B. 이벤트 기반 동기화 (Outbox 패턴과 결합)**
```
Lua Script로 Redis 재고 차감 → 이벤트 발행 → Consumer가 DB stock UPDATE
```
- 장점: 분리된 관심사, Redis 장애 시 이벤트 재처리로 복구 가능
- 단점: 최종 일관성(eventual consistency). 순간적인 Redis-DB 불일치 허용 필요

→ 이 프로젝트에서는 Week 3 Kafka/Outbox 스프린트와 결합해 **옵션 B**를 적용할 예정.

### Redis 장애 시 Fallback

Redis가 내려갈 경우 Lua Script를 사용할 수 없으므로,
Circuit Breaker 패턴으로 낙관적 락(`@Version`) 경로로 자동 전환하는 Fallback을 고려한다.

---

## 결론 및 실행 계획

| 단계 | 내용 | 시점 |
|------|------|------|
| 1 | 낙관적 락(`@Version`) 유지 (현재 상태) | Sprint 1 |
| 2 | 동시성 실험 실행 → 충돌률 측정 | Sprint 1 후반 |
| 3 | 충돌률 수치 보고 Lua Script 전환 여부 결정 | 측정 직후 |
| 4 | Lua Script 도입 + Redis 워밍업 + Fallback 설계 | Week 2 |
| 5 | Outbox 패턴과 결합해 Redis-DB 동기화 안정화 | Week 3 |
