package com.project.payflow.domain.payment.enums;

public enum PaymentStatus {
    PENDING, // Payment 레코드 생성 시 초기 상태. PG사 응답 전 단계
    COMPLETED, // PG사로부터 승인 응답을 받은 상태. pg_transcation_id 세팅됨
    FAILED, // PG사 거절 또는 타임아웃 등 실패 상태
    CANCELLED, // 결제 완료 전 취소 (PENDING -> CANCELLED)
    REFUNDED // 결제 완료 후 환불 승인 (COMPLETED -> REFUNDED)
}
