package com.project.payflow.domain.payment.repository;

import com.project.payflow.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long>{
    Optional<Payment> findByOrder_Id(Long orderId);
}