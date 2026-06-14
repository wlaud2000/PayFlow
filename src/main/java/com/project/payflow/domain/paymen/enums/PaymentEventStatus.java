package com.project.payflow.domain.paymen.enums;

public enum PaymentEventStatus {
    PENDING, // PaymentEvent INSERT 시 초기 상태, PG 처리 중임을 표시
    COMPLETED, // PG 처리 성공 완료
    FAILED // PG 처리 실패. 재시도 여부는 서비스 레이어에서 판단
}
