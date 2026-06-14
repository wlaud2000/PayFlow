package com.project.payflow.domain.order.enums;

public enum OrderStatus {
    PENDING, // 주문 최초 생성 시 초기 상태. 결제 전 단계
    PAYING, // 결제 진행 중 상태. PG사 AI 호출 시점에 전이
    PAID, // 결제 성공 확정 상태
    CANCELLED, // 결제 전 또는 결제 실패 시 취소
    REFUNDED // 결제 완료 후 환불 처리 완료
}
