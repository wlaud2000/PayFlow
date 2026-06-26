package com.project.payflow.domain.payment.toss.exception;

import com.project.payflow.domain.payment.toss.dto.TossErrorResponse;

// Toss API 에러 코드 ALREADY_PROCESSED_PAYMENT에만 매핑
// 4xx를 전부 TossPaymentsClientException으로 처리하면 이 케이스를 로그에서 구분할 수 없음
// 운영에서 이 예외가 잡히면 "멱등성 처리가 뚫렸거나 클라이언트 중복 요청 버그"라는 즉각적인 신호가 됨
public class PaymentAlreadyApprovedException extends TossPaymentsClientException {
    public PaymentAlreadyApprovedException(TossErrorResponse error){
        super(error);
    }
}