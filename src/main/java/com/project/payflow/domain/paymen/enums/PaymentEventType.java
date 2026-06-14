package com.project.payflow.domain.paymen.enums;

// 멱등성 키는 orderId:{eventType.name()} 조합으로 생성됨
// 타입이 다르면 같은 orderId라도 키가 달라지므로 CONFIRM과 REFUND는 독립적으로 처리됨
public enum PaymentEventType {
    CONFIRM,
    CANCEL,
    REFUND
}
