package com.project.payflow.domain.payment;

import com.project.payflow.domain.payment.entity.Payment;
import com.project.payflow.domain.payment.entity.PaymentEvent;
import com.project.payflow.domain.payment.enums.PaymentEventType;
import com.project.payflow.domain.payment.repository.PaymentEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentIdempotencyManager {

    private final PaymentEventRepository paymentEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW) // -> 외부 트랜잭션을 일시 정지하고 독립적인 신규 트랜잭션을 열어 실행
    public Optional<PaymentEvent> tryCreate(Payment payment, String idempotencyKey, PaymentEventType eventType) {
        try {
            paymentEventRepository.saveAndFlush(PaymentEvent.pending(payment, idempotencyKey, eventType));
            return Optional.empty();
        } catch (DataIntegrityViolationException e) {
            return paymentEventRepository.findByIdempotencyKey(idempotencyKey);
        }
    }
}
