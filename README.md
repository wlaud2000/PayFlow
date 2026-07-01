# PayFlow

## 프로젝트 배경

카드사는 제휴 가맹점과 함께 한정수량 특가 캠페인을 정기적으로 운영한다.
캠페인 오픈 순간 수천 명의 동시 결제 요청이 몰리는 상황에서
재고 초과 판매, 중복 결제, PG사 장애 등의 문제가 발생할 수 있다.
PayFlow는 이 시나리오를 기반으로 설계된 고신뢰 결제 처리 시스템이다.

## 핵심 기술 선택 배경 (시나리오 연결)

- **선착순 한정수량 → 동시 재고 차감 경쟁** → Redis Lua Script (원자적 처리)
- **네트워크 순단/사용자 재시도 → 중복 결제 위험** → DB UNIQUE 멱등성 키
- **캠페인 피크 트래픽 → PG사(토스페이먼츠) 간헐적 장애** → Circuit Breaker + Retry + Fallback
- **보상 트랜잭션 실패 감사** → REQUIRES_NEW 독립 트랜잭션으로 보상 로그 영구 보존

## ERD

```mermaid
erDiagram
    MEMBER {
        bigint id PK
        varchar email UK
        varchar password
        varchar name
        datetime created_at
        datetime updated_at
    }

    PRODUCT {
        bigint id PK
        varchar name
        bigint price
        int stock
        bigint version
        text campaign_description
        datetime created_at
        datetime updated_at
    }

    ORDERS {
        bigint id PK
        bigint member_id FK
        bigint product_id FK
        int quantity
        varchar status
        datetime created_at
        datetime updated_at
    }

    PAYMENT {
        bigint id PK
        bigint order_id FK
        bigint amount
        varchar status
        varchar pg_transaction_id
        datetime created_at
        datetime updated_at
    }

    PAYMENT_EVENT {
        bigint id PK
        bigint payment_id FK
        varchar idempotency_key UK
        varchar event_type
        varchar status
        datetime created_at
    }

    MEMBER ||--o{ ORDERS : "places"
    PRODUCT ||--o{ ORDERS : "ordered in"
    ORDERS ||--o{ PAYMENT : "paid by"
    PAYMENT ||--o{ PAYMENT_EVENT : "logged as"
```
