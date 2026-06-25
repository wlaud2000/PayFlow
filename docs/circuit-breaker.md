# Circuit Breaker 패턴 및 Resilience4j 사전 분석

## 1. 왜 Circuit Breaker가 필요한가

### 현재 구조의 문제

`PaymentService.confirmPayment()`는 Toss Payments API를 동기 HTTP로 호출한다.

```
정상 상황: 요청 → TossPaymentsClient.confirm() → 200ms 응답
장애 상황: 요청 → TossPaymentsClient.confirm() → ... (타임아웃까지 대기)
```

Toss API가 응답하지 않으면 해당 스레드는 타임아웃(수초~수십초)까지 블로킹된다.
동시 요청이 N건이면 N개 스레드가 모두 블로킹된다.

```
요청 1 → 스레드 1 블로킹 (10초 대기 중)
요청 2 → 스레드 2 블로킹 (10초 대기 중)
요청 3 → 스레드 3 블로킹 (10초 대기 중)
...
요청 N → 스레드 풀 고갈 → 결제 외 API도 응답 불가
```

**문제의 핵심:** Toss Payments API 장애 하나가 전체 서비스 장애로 전파된다.
Circuit Breaker는 이 전파를 차단한다.

---

## 2. Circuit Breaker 상태 전이

```
            실패율 50% 초과
  CLOSED ──────────────────▶ OPEN
     ▲                         │
     │                    10초 경과
     │                         ▼
     │              ┌────── HALF_OPEN
     │  테스트 성공 │              │ 테스트 실패
     └──────────────┘              │
                                   ▼
                                 OPEN
```

### CLOSED — 정상 상태

- 모든 요청이 실제 서비스(Toss API)로 전달된다
- 슬라이딩 윈도우(최근 10건)로 실패율을 추적한다
- 실패율이 50%를 초과하면 OPEN으로 전환한다

### OPEN — 차단 상태

- 모든 요청을 Toss API 호출 없이 즉시 `CallNotPermittedException`으로 거부한다
- 스레드를 블로킹하지 않으므로 스레드 풀이 보호된다
- 10초 후 HALF_OPEN으로 자동 전환된다

### HALF_OPEN — 복구 확인 상태

- 제한된 수의 테스트 요청만 실제 Toss API로 전달한다
- 테스트 요청이 성공하면 CLOSED로 복귀한다
- 테스트 요청이 실패하면 다시 OPEN으로 진입한다

---

## 3. 라이브러리 비교 및 선택

### 비교 대상

| 항목 | Resilience4j | Spring Retry | 직접 구현 |
|------|-------------|-------------|---------|
| Circuit Breaker 상태 관리 | ✅ CLOSED/OPEN/HALF_OPEN 완전 지원 | ⚠️ 단순 재시도만 | ❌ edge case 직접 처리 필요 |
| Actuator 메트릭 자동 노출 | ✅ | ❌ | ❌ |
| WebClient 통합 | ✅ 리액티브/동기 모두 지원 | ❌ | ❌ |
| Hystrix 대체 표준 | ✅ Netflix 공식 후계자 | ❌ | ❌ |
| 학습 비용 | 중간 | 낮음 | 높음 |

### 선택: Resilience4j

**Spring Retry를 선택하지 않은 이유:**
Spring Retry의 `@CircuitBreaker`는 HALF_OPEN 상태 관리와 슬라이딩 윈도우 기반 실패율 계산을 지원하지 않는다. 단순 재시도(`@Retryable`)에는 적합하지만, 외부 API 장애로부터 시스템을 보호하는 Circuit Breaker 본래 목적에는 부족하다.

**직접 구현을 선택하지 않은 이유:**
`AtomicReference<State>`로 상태를 관리하고 슬라이딩 윈도우를 구현하는 것은 학습 목적으로는 좋다. 그러나 동시성 제어, HALF_OPEN 테스트 호출 수 제한, 타임아웃 처리 등 edge case를 프로덕션 수준으로 다루려면 Resilience4j와 거의 같은 코드를 작성해야 한다.

**Resilience4j를 선택한 이유:**
- CLOSED/OPEN/HALF_OPEN 상태 전이를 완전히 지원한다
- Spring Boot Actuator와 통합되면 Circuit Breaker 상태가 `/actuator/health`에 자동 노출된다
- Week 4 Prometheus 연동 시 `resilience4j.circuitbreaker.*` 메트릭이 자동으로 수집된다
- `TossPaymentsClient`에 `@CircuitBreaker` 어노테이션 하나로 적용 가능하다

---

## 4. 설정값 결정 근거

### `failureRateThreshold = 50`

최근 10건 중 5건 이상 실패하면 OPEN으로 전환한다.

- **30%로 낮추면:** 일시적 네트워크 오류 1~2건에도 Circuit이 열린다. 정상 상황에서 불필요하게 차단될 수 있다.
- **80%로 높이면:** 명백한 장애 상황에서도 요청이 계속 Toss API로 전달된다. 스레드 블로킹이 오래 지속된다.
- **50%:** "절반 이상 실패 = 일시적 오류가 아닌 실제 장애"라는 기준으로, 오탐과 반응 속도의 균형점이다.

### `waitDurationInOpenState = 10s`

OPEN 상태 유지 시간. 10초 후 HALF_OPEN으로 자동 전환한다.

- **1초로 낮추면:** Toss API가 아직 복구 중인데 HALF_OPEN 테스트 호출이 나가고, 실패하면 다시 OPEN으로 반복된다. 복구를 방해한다.
- **60초로 높이면:** Toss API가 5초 만에 복구되어도 55초 동안 결제가 불가능하다.
- **10초:** 외부 결제 API의 일반적인 재시작/복구 시간(수초~수십초)을 커버하면서, 복구 후 빠르게 정상 전환할 수 있는 균형점이다.

### `slidingWindowSize = 10`

실패율 계산에 사용하는 최근 요청 수.

- **3건으로 줄이면:** 표본이 너무 작아 통계적으로 불안정하다. 우연한 실패 2건(66%)만으로 Circuit이 열릴 수 있다.
- **100건으로 늘리면:** 장애 발생 후 Circuit이 열리기까지 많은 요청이 실패해야 한다. 반응이 느려진다.
- **10건:** Resilience4j 기본값이며, 저~중트래픽 서비스에서 통계적 안정성과 반응 속도를 모두 만족하는 값이다.

---

## 5. 적용 위치 결정

### `TossPaymentsClient` 레벨에 적용

```
PaymentService.confirmPayment()
  └─ TossPaymentsClient.confirm()  ← @CircuitBreaker 적용
```

**`PaymentService` 레벨에 적용하지 않은 이유:**
Circuit Breaker는 "Toss API 연결이 건강한가"를 판단하는 인프라 관심사다. `PaymentService`는 결제 비즈니스 로직을 담당하는 계층으로, Circuit Breaker 상태 관리를 여기에 두면 단일 책임 원칙이 깨진다.

**`TossPaymentsClient` 레벨에 적용하는 이유:**
- `confirm()` 외에 `cancel()`(환불)도 같은 Toss API를 호출한다. 클라이언트 레벨에 적용하면 모든 Toss API 호출이 동일하게 보호된다.
- Toss API 장애는 개별 비즈니스 메서드의 문제가 아니라 연결 자체의 문제이므로, 클라이언트에서 차단하는 것이 의미적으로 정확하다.

---

## 6. 구현 계획 (Sprint 3 구현 단계 사전 설계)

```yaml
# application.yml
resilience4j:
  circuitbreaker:
    instances:
      tossPayments:
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
        slidingWindowSize: 10
        permittedNumberOfCallsInHalfOpenState: 3
```

```java
// TossPaymentsClient
@CircuitBreaker(name = "tossPayments", fallbackMethod = "confirmFallback")
public TossConfirmResponse confirm(TossConfirmRequest request) { ... }

private TossConfirmResponse confirmFallback(TossConfirmRequest request, CallNotPermittedException e) {
    throw new PaymentException(PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
}
```

적용 대상 메서드: `confirm()`, `cancel()`
fallback 전략: 즉시 `PaymentException` throw → 상위에서 결제 실패 응답 반환
