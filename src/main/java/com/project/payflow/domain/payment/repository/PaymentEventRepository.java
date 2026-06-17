package com.project.payflow.domain.payment.repository;

import com.project.payflow.domain.payment.entity.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long>{
    Optional<PaymentEvent> findByIdempotencyKey(String idempotencyKey);
}