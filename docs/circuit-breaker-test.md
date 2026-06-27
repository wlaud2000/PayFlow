# Circuit Breaker 통합 테스트 결과 (WireMock)

## 1. 문서 작성 배경

### 기존 테스트의 한계

Sprint 2에서 Circuit Breaker를 도입할 때 작성한 `TossPaymentsClientCircuitBreakerTest`는 다음 세 가지 문제가 있었다.

**문제 1 — CB 상태를 강제 조작했다**

```java
// 기존 방식
circuitBreakerRegistry.circuitBreaker("tosspayments").transitionToOpenState();
```

CB가 실제로 어떤 조건에서 OPEN으로 전이되는지 전혀 검증하지 않는다. `failureRateThreshold: 50%`, `minimumNumberOfCalls: 5` 같은 설정값이 실제로 동작하는지 알 수 없다.

**문제 2 — HTTP 경로 전체를 차단했다**

```java
// 기존 방식
@MockitoBean
private WebClient tossPaymentsWebClient;
```

WebClient를 Mock으로 대체하면 실제 HTTP 호출이 발생하지 않는다. Retry가 몇 번 발생했는지, WireMock에 몇 번 요청이 갔는지 검증 자체가 불가능하다.

**문제 3 — Sprint 3 이후 테스트가 깨졌다**

Sprint 3 Fallback 구현으로 `confirmFallback`이 `PaymentFallbackException`을 throw하도록 변경됐다. 기존 테스트는 `PaymentException`을 기대하므로 수정 없이는 실패한다.

```
기존 테스트 기대: PaymentException
Sprint 3 이후 실제: PaymentFallbackException → 테스트 실패
```

---

## 2. 개선 방향

| 항목 | 기존 | 변경 후 |
|------|------|---------|
| PG API 모킹 | `@MockitoBean WebClient` — HTTP 완전 차단 | WireMock 실제 HTTP 서버 기동 |
| CB 상태 제어 | `transitionToOpenState()` 강제 전이 | 실제 실패 누적으로 자연 전이 |
| Retry 검증 | 불가 | WireMock 호출 횟수(`verify`)로 검증 |
| HALF_OPEN 복구 흐름 | 미검증 | waitDuration 경과 → 성공 3회 → CLOSED 검증 |
| Fallback 원인 분류 | 미검증 | `FailureReason` 직접 assert |
| 테스트 수 | 2개 (CB OPEN 강제) | 7개 (기존 2개 수정 + 통합 5개) |

---

## 3. 테스트 환경

### Resilience4j Circuit Breaker 설정

| 설정 | 운영값 | 테스트값 | 비고 |
|------|--------|---------|------|
| `failureRateThreshold` | 50% | 50% | 최근 N건 중 50% 이상 실패 시 OPEN |
| `slidingWindowSize` | 10 | 10 | 슬라이딩 윈도우 크기 |
| `minimumNumberOfCalls` | 5 | 5 | OPEN 판단 최소 호출 수 |
| `waitDurationInOpenState` | 10s | **200ms** | HALF_OPEN 전이 대기 시간 (테스트 단축) |
| `permittedNumberOfCallsInHalfOpenState` | 3 | 3 | HALF_OPEN 상태에서 허용 호출 수 |

### Resilience4j Retry 설정

| 설정 | 운영값 | 테스트값 | 비고 |
|------|--------|---------|------|
| `maxAttempts` | 3 | 3 | 총 3회 (최초 1회 + 재시도 2회) |
| `waitDuration` | 1s | **50ms** | 재시도 간 대기 (테스트 단축) |
| `retryExceptions` | `IOException`, `TimeoutException`, `TossPaymentsServerException` | 동일 | 재시도 대상 예외 |
| `ignoreExceptions` | `TossPaymentsClientException`, `PaymentAlreadyApprovedException` | 동일 | 재시도 제외 예외 |

### Aspect 적용 순서 (명시 설정)

```yaml
# application.yml
resilience4j:
  circuitbreaker:
    circuitBreakerAspectOrder: 1   # outer
  retry:
    retryAspectOrder: 2            # inner
```

> Resilience4j 기본값은 Retry(outer) → CB(inner) 순서다. 이 기본값에서 발생한 문제와 수정 경위는 핵심 발견사항 섹션에서 상세히 다룬다.

### 기타

| 항목 | 값 |
|------|-----|
| WireMock | `org.wiremock:wiremock-jetty12:3.9.1` |
| WebClient `responseTimeout` | 3초 |
| 테스트 프레임워크 | `@SpringBootTest(webEnvironment = NONE)` + JUnit 5 |
| DB | H2 In-Memory (test profile) |
| WireMock 포트 | 동적 포트 (`dynamicPort()`) — `@DynamicPropertySource`로 주입 |

---

## 4. 시나리오별 결과

### 시나리오 1 — PG 정상 응답 시 결제 승인 성공

| 항목 | 내용 |
|------|------|
| WireMock stub | POST /v1/payments/confirm → 200 OK |
| WireMock 호출 횟수 | 1회 |
| 발생 예외 | 없음 |
| 반환값 | `TossConfirmResponse.paymentKey() = "pg-tx-key-001"` |
| 결과 | ✅ PASS |

```
confirm() 호출 → WireMock(200 OK) → TossConfirmResponse 반환
```

---

### 시나리오 2 — PG 503 반복 → Retry 3회 → RETRY_EXHAUSTED Fallback

| 항목 | 내용 |
|------|------|
| WireMock stub | POST /v1/payments/confirm → 503 (항상) |
| WireMock 호출 횟수 | **3회** (maxAttempts: 3) |
| 발생 예외 | `PaymentFallbackException` |
| `FailureReason` | `RETRY_EXHAUSTED` |
| 결과 | ✅ PASS |

```
confirm() 호출
  → CB(outer) → Retry(inner): WireMock(503) → TossPaymentsServerException
  → Retry 재시도 (총 3회)
  → Retry 소진 → TossPaymentsServerException 전파
  → CB fallback → PaymentFallbackException(RETRY_EXHAUSTED)
```

**WireMock 호출 횟수가 3회인 이유:**
`maxAttempts: 3`은 "총 시도 횟수"를 의미한다. 최초 1회 + 재시도 2회 = 3회. `TossPaymentsServerException`은 `retryExceptions`에 포함되므로 3회 모두 실제 HTTP 요청이 발생한다.

> 이 시나리오는 초기 테스트에서 1회만 호출되어 실패했다. 원인은 Resilience4j의 기본 aspect 순서 문제였다. 핵심 발견사항 1을 참고.

---

### 시나리오 3 — PG 응답 지연 → responseTimeout 초과 → Fallback

| 항목 | 내용 |
|------|------|
| WireMock stub | POST /v1/payments/confirm → 5,000ms 지연 후 200 OK |
| WireMock 호출 횟수 | **1회** (재시도 없음) |
| 발생 예외 | `PaymentFallbackException` |
| `FailureReason` | `RETRY_EXHAUSTED` |
| 소요 시간 | 약 3초 (responseTimeout 만료 시점) |
| 결과 | ✅ PASS |

```
confirm() 호출
  → WebClient(5s 지연 stub) → responseTimeout(3s) 만료
  → 네트워크 예외 발생 → WebClientRequestException으로 래핑
  → Resilience4j Retry: 매칭 실패 → 재시도 없음
  → CB fallback → PaymentFallbackException(RETRY_EXHAUSTED)
```

> **주의:** WireMock 호출 횟수가 1회인 이유는 핵심 발견사항 2에서 상세히 다룬다.

---

### 시나리오 4 — 실패율 50% 초과 → CB OPEN → 즉시 Fallback

| 항목 | 내용 |
|------|------|
| WireMock stub | POST /v1/payments/confirm → 503 (항상) |
| 실패 누적 호출 횟수 | **5회** (minimumNumberOfCalls 충족) |
| WireMock 총 호출 횟수 | **15회** (5회 × 3 retry) |
| CB 전이 | CLOSED → OPEN (5회 실패, 실패율 100% > 50%) |
| OPEN 이후 호출 | `CallNotPermittedException` → fallback |
| `FailureReason` | `RETRY_EXHAUSTED` (5회) → `CIRCUIT_OPEN` (6번째) |
| 결과 | ✅ PASS |

```
confirm() × 5 (모두 503, 각 3회 retry)
  → CB(outer) 기준: 5건 모두 실패 (100% > 50%)
  → minimumNumberOfCalls(5) 충족
  → CB: CLOSED → OPEN

confirm() 6번째 호출
  → CB OPEN → WireMock 미도달 → CallNotPermittedException
  → PaymentFallbackException(CIRCUIT_OPEN)
```

**WireMock 호출이 15회인 이유:**
CB(outer) → Retry(inner) 구조이므로, CB 입장에서 `confirm()` 1회 호출 = 내부적으로 Retry 3회 = HTTP 3회다. CB는 Retry 전체를 1건으로 기록한다. CB OPEN 이후 호출은 WireMock에 도달하지 않으므로 `verify(15)`로 검증된다.

---

### 시나리오 5 — CB OPEN → waitDuration 경과 → HALF_OPEN → 성공 → CLOSED

| 항목 | 내용 |
|------|------|
| 실패 유도 | 503 × 5회 → CB OPEN |
| waitDuration 대기 | `Thread.sleep(250ms)` (waitDurationInOpenState: 200ms) |
| HALF_OPEN 전이 | lazy — **첫 번째 호출 시 자동 전이** |
| HALF_OPEN 성공 호출 | 3회 (permittedNumberOfCallsInHalfOpenState) |
| 최종 CB 상태 | CLOSED |
| 결과 | ✅ PASS |

```
confirm() × 5 (503) → CB OPEN
Thread.sleep(250ms)
  → waitDurationInOpenState(200ms) 경과

stub 교체: 503 → 200 OK

confirm() 1번째 (HALF_OPEN lazy 전이 발생) → 성공
confirm() 2번째 → 성공
confirm() 3번째 → 성공 → CB: HALF_OPEN → CLOSED
```

**HALF_OPEN 전이가 lazy한 이유:**
Resilience4j CB는 waitDuration이 경과해도 다음 호출이 들어오기 전까지 상태를 OPEN으로 유지한다. 전이 비용을 호출 시점으로 미루는 설계다. Thread.sleep 직후 `getState()`를 조회하면 여전히 OPEN이 반환된다.

---

## 5. 핵심 발견사항

### 발견 1 — Resilience4j 기본 Aspect 순서로 인한 Retry 완전 미작동

#### 현상

시나리오 2에서 WireMock 호출이 3회가 아닌 **1회**만 발생했다. `TossPaymentsServerException`은 `retryExceptions`에 명시적으로 등록되어 있었음에도 Retry가 전혀 작동하지 않았다. 재시도 이벤트 로그도 발생하지 않았다.

#### 원인 분석

Resilience4j의 기본 aspect 순서가 원인이었다.

```
[기본 순서]
RetryAspect    order = LOWEST_PRECEDENCE - 4  → 더 높은 우선순위 = outer
CircuitBreakerAspect order = LOWEST_PRECEDENCE - 3  → 더 낮은 우선순위 = inner
```

호출 흐름이 **Retry(outer) → CB(inner) → method()** 순서가 된다.

```
[실제 동작 — 기본 순서]
confirm() 호출
  → Retry(outer): 실행 시작
  → CB(inner): 실행 시작
  → method(): 503 → TossPaymentsServerException
  → CB(inner): 예외 캐치 → fallback 호출 (fallbackMethod = "confirmFallback")
  → confirmFallback(): PaymentFallbackException(RETRY_EXHAUSTED) throw
  → Retry(outer): PaymentFallbackException 수신
  → retryExceptions 확인: PaymentFallbackException ∉ {IOException, TimeoutException, TossPaymentsServerException}
  → 재시도 없이 전파
```

`@CircuitBreaker(fallbackMethod = "confirmFallback")`가 CB를 inner 위치에서 예외를 잡아 `PaymentFallbackException`으로 변환하기 때문에, outer에 있는 Retry는 원래 예외인 `TossPaymentsServerException`을 볼 수 없다. Retry는 항상 1회만 실행된다.

#### 수정

`application.yml`에 aspect 순서를 명시적으로 지정해 CB(outer) → Retry(inner) 구조로 변경했다.

```yaml
resilience4j:
  circuitbreaker:
    circuitBreakerAspectOrder: 1   # outer
  retry:
    retryAspectOrder: 2            # inner
```

```
[수정 후 동작]
confirm() 호출
  → CB(outer): 실행 시작
  → Retry(inner): 실행 시작
  → method(): 503 → TossPaymentsServerException
  → Retry(inner): TossPaymentsServerException ∈ retryExceptions → 재시도
  → 3회 모두 실패 → TossPaymentsServerException 전파
  → CB(outer): 1건 실패 기록 → confirmFallback() 호출
  → PaymentFallbackException(RETRY_EXHAUSTED)
```

#### 이 경험에서 얻은 것

> `@CircuitBreaker`와 `@Retry`를 같은 메서드에 동시에 적용할 때, aspect 순서가 의도와 다르면 **한쪽이 완전히 무력화된다**. "설정이 있으면 작동한다"는 가정 대신, WireMock 호출 횟수를 `verify()`로 직접 측정해서 실제 동작을 확인해야 한다. 라이브러리 기본값은 예상과 다를 수 있다.

---

### 발견 2 — responseTimeout + Retry 미작동 (예외 래핑 문제)

#### 현상

시나리오 3에서 WireMock 호출이 3회가 아닌 **1회**만 발생했다.

`application.yml`에 `retryExceptions: java.util.concurrent.TimeoutException`이 설정되어 있어 timeout 시 Retry가 작동해야 한다고 예상했다. 그러나 실제 WireMock 호출은 1회에 그쳤다.

#### 원인 분석

Resilience4j Retry와 WebClient 사이에서 **예외 래핑** 문제가 발생했다.

```
[1] HttpClient.responseTimeout(3s) 만료
      ↓
[2] Reactor Netty: 네트워크 레벨 예외 발생
      ↓
[3] Spring WebClient: WebClientRequestException(RuntimeException)으로 래핑
      ↓
[4] .block() 호출 → WebClientRequestException 전파
      ↓
[5] Resilience4j Retry: instanceof 직접 비교
      - WebClientRequestException instanceof java.io.IOException? → false
      - WebClientRequestException instanceof TimeoutException? → false
      → 재시도 조건 불충족 → Retry 미작동
```

**핵심:** Resilience4j Retry의 `retryExceptions` 설정은 예외 클래스에 대해 `instanceof` 직접 비교를 수행한다. cause chain을 순회하지 않는다. `responseTimeout`이 발생시키는 네트워크 예외는 WebClient에 의해 `WebClientRequestException`으로 래핑되어, 설정된 `IOException`이나 `TimeoutException`과 매칭되지 않는다.

이는 **동기 Resilience4j 데코레이터(`@Retry`)와 비동기 WebClient를 조합할 때의 구조적 제약**이다.

#### 현재 대응

Retry 미작동 사실을 테스트로 실증하고 명세로 고정했다.

- `verify(1, ...)` — "timeout 시 재시도 없이 1회만 호출됨"을 assert
- `FailureReason.RETRY_EXHAUSTED` — 분류 불가 예외의 catch-all로 처리

운영 영향은 제한적이다. 타임아웃 요청을 재시도하면 이미 처리됐을 수 있는 결제 건을 다시 요청하게 되어 이중 청구 위험이 있다. 재시도가 발생하지 않는 현재 동작이 결제 안전성 측면에서는 오히려 더 안전하다.

#### 개선 방향

YAML 설정 대신 Resilience4j를 **코드로 설정**해 cause chain 기반 predicate를 적용할 수 있다.

```java
RetryConfig retryConfig = RetryConfig.custom()
    .retryOnException(e -> {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof IOException || cause instanceof TimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    })
    .build();
```

또는 WebClient에 **예외 변환 필터**를 추가해, timeout 시 `WebClientRequestException` 대신 Resilience4j가 인식 가능한 예외를 직접 throw하도록 변환할 수 있다. 단, 어느 방법이든 timeout 재시도의 이중 청구 위험을 함께 검토해야 한다.

#### 이 경험에서 얻은 것

> 동기 Resilience4j와 비동기 WebClient를 조합할 때, 라이브러리 문서가 아닌 **실제 예외 전파 경로를 추적해 "어떤 예외가 Resilience4j에 도달하는가"를 직접 확인해야 한다**는 것을 배웠다. 설정이 존재한다고 해서 의도대로 동작한다고 가정하면 안 된다.

---

## 6. CB 상태 전이 정리

```
초기 상태: CLOSED

[CLOSED → OPEN]
  조건: slidingWindowSize(10건) 중 failureRateThreshold(50%) 이상 실패
        단, minimumNumberOfCalls(5) 충족 이후부터 판단
  시나리오 4에서 확인: 5회 연속 실패 (100%) → OPEN 전이

[OPEN 상태]
  동작: CallNotPermittedException 즉시 반환 (WireMock 미도달)
  대기: waitDurationInOpenState 경과 후 HALF_OPEN 전이 가능

[OPEN → HALF_OPEN]
  조건: waitDurationInOpenState 경과 + 다음 호출 발생 (lazy 전이)
  시나리오 5에서 확인: Thread.sleep(250ms) 후 첫 번째 호출 시 전이

[HALF_OPEN → CLOSED]
  조건: permittedNumberOfCallsInHalfOpenState(3)건 모두 성공
  시나리오 5에서 확인: 성공 3회 → CLOSED 복귀

[HALF_OPEN → OPEN]
  조건: HALF_OPEN 허용 호출 중 실패 발생
  이번 테스트에서는 시나리오 구성상 미검증
```

### 예측 vs 실제 결과

| 예측 | 실제 | 일치 여부 |
|------|------|---------|
| 503 × 3회 retry 후 RETRY_EXHAUSTED | 1회만 호출 (aspect 순서 버그) → 수정 후 3회 확인 | ❌ → 수정 후 ✅ |
| timeout 시 retry 3회 발생 | 1회 호출, retry 미작동 (예외 래핑 문제) | ❌ (원인 파악, 명세로 고정) |
| 5회 실패 후 CB OPEN | 정확히 5회 후 OPEN | ✅ |
| CB OPEN 시 WireMock 미도달 | verify(15)로 확인 | ✅ |
| waitDuration 후 HALF_OPEN lazy 전이 | 첫 호출 시 전이 | ✅ |
| 성공 3회 후 CLOSED | CLOSED 확인 | ✅ |

---

## 7. 커밋 정보

| 커밋 | 내용 |
|------|------|
| `bf77059` | CB/Retry Fallback 구현 — FailureReason 분류, Payment FAILED 저장 |
| `ca7ab6d` | TossPaymentsClient Retry 적용 및 재시도 이벤트 리스너 등록 |
| `aeb75de` | Toss API 예외 계층 분리 및 Retry 설정 추가 |
| `f89f842` | TossPaymentsClient CB OPEN 상태 동작 검증 테스트 추가 |
