# k6 부하 테스트 결과

## 테스트 환경

| 항목 | 값 |
|------|----|
| 도구 | k6 |
| 시나리오 | 로그인 → 주문 생성 → 결제 요청 → 결제 확인 |
| 대상 상품 재고 | 100,000개 |
| Toss API | WireMock stub (즉시 200 응답) |
| 서버 프로파일 | local, load-test |
| 부하 패턴 | warmup 50 VU (30s) → peak 200 VU (60s) → ramp-down (30s) |

---

## 1차 — HikariCP 기본값 (pool = 10), 재시도 없음

| 지표 | 값 |
|------|----|
| TPS | 274 req/s |
| P95 응답시간 | 19.68ms |
| 에러율 | 21.90% |
| order created | ✅ 100% |
| payment requested | ✅ 100% |
| payment confirmed | ❌ 34% |
| threshold 통과 | ❌ |

---

## 2차 — HikariCP pool = 50, 재시도 없음

| 지표 | 값 |
|------|----|
| TPS | 271 req/s |
| P95 응답시간 | 33.88ms |
| 에러율 | 22.15% |
| order created | ✅ 100% |
| payment requested | ✅ 100% |
| payment confirmed | ❌ 33% |
| threshold 통과 | ❌ |

---

## 3차 — HikariCP pool = 50, OptimisticLock 재시도 3회 추가

| 지표 | 값 |
|------|----|
| TPS | 274 req/s |
| P95 응답시간 | 57.4ms |
| 에러율 | 0.20% |
| order created | ✅ 100% |
| payment requested | ✅ 100% |
| payment confirmed | ✅ 99% |
| threshold 통과 | ✅ |

---

## 개선 과정 요약

| | 1차 | 2차 | 3차 |
|-|-----|-----|-----|
| 가설 | HikariCP 병목 | — | OptimisticLock 재시도 |
| payment confirmed | 34% | 33% | **99%** |
| 에러율 | 21.90% | 22.15% | **0.20%** |
| P95 | 19.68ms | 33.88ms | 57.4ms |

---

## 병목 분석

### 가설 — HikariCP 커넥션 풀 고갈

HikariCP 기본 pool(10)이 200 VU를 감당하지 못해 커넥션 대기 → P95 급등을 예상했다.

**결과: 틀린 가설.** 1차·2차 P95가 각각 19ms, 33ms로 매우 빠르고, pool 확장 전후 수치가 거의 동일했다. 커넥션 대기가 병목이었다면 P95가 수백ms로 치솟았을 것이다.

### 실제 병목 — OptimisticLockException

`payment confirmed` 단계에서 66% 실패의 실제 원인은 `ObjectOptimisticLockingFailureException`이었다.

`Product` 엔티티의 `@Version` 필드로 인해 200 VU가 동시에 같은 상품 행을 업데이트하면:
```sql
UPDATE product SET stock = stock - 1, version = version + 1
WHERE id = 1 AND version = ?   -- 매 순간 1건만 성공, 나머지 version 불일치로 실패
```

기존 코드는 재시도 없이 곧바로 500 에러를 반환했다.

### 해결 — REQUIRES_NEW + 지수 백오프 재시도

`ProductStockService.decreaseWithOptimisticLock()` (3회 재시도, 50ms 지수 백오프)를 활용하도록 `PaymentService.confirmPayment()`의 DB 재고 차감 경로를 교체했다.

`@Transactional(propagation = Propagation.REQUIRES_NEW)` 덕분에 재시도마다 새 트랜잭션이 열려, 실패한 시도가 외부 결제 트랜잭션을 오염시키지 않는다.

**결과: payment confirmed 34% → 99%, 에러율 21.90% → 0.20%**

P95가 19ms → 57ms로 증가한 것은 재시도 대기 시간(50ms 백오프)이 포함됐기 때문이며, 여전히 500ms SLA 내에 있다.

---

## 재고 정확성 검증

```sql
SELECT stock FROM product WHERE id = 1;
```

Redis Lua script가 DB보다 먼저 원자적으로 차감되므로, OptimisticLock 재시도 실패 시에도 Redis 재고는 감소한 상태를 유지한다. `StockSyncBatchService`(10분 주기)가 Redis/DB 불일치를 감지하고 로그로 알린다.
