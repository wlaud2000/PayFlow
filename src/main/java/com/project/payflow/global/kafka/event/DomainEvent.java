package com.project.payflow.global.kafka.event;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Getter;

import java.time.LocalDateTime;

// [설계 결정] abstract class 선택 이유
// 1토픽 = 1이벤트 타입 구조라 다형성 역직렬화가 불필요.
// @SuperBuilder 대신 각 자식 클래스에 @NoArgsConstructor(PROTECTED) + 정적 팩토리 of() 패턴 사용.
// Jackson이 @NoArgsConstructor로 객체 생성 후 필드 바인딩하는 방식으로 역직렬화 처리.
//
// [Known Limitation] occurredAt: LocalDateTime 사용
// 단일 서버 환경에서 타임존 불일치 문제 없음.
// 다중 지역 배포 시 Instant로 마이그레이션 필요. (version 필드가 이 확장 가능성을 위한 것)
@Getter
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public abstract class DomainEvent {

    private String eventId;        // UUID — 이벤트 자체의 고유 ID (멱등성 키)
    private String eventType;      // String 타입: Enum 미사용 — 서비스 분리 시 공유 의존성 제거 목적
    private LocalDateTime occurredAt;
    private int version;           // 스키마 버전 (하위 호환성 관리)

    protected DomainEvent() {}     // Jackson 역직렬화용

    protected DomainEvent(String eventId, String eventType, LocalDateTime occurredAt, int version) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.version = version;
    }
}