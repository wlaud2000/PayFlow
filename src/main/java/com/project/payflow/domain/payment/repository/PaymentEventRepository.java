package com.project.payflow.domain.payment.repository;

import com.project.payflow.domain.payment.entity.PaymentEvent;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long>{
    Optional<PaymentEvent> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT pe FROM PaymentEvent pe WHERE pe.idempotencyKey = :key")
    Optional<PaymentEvent> findByIdempotencyKeyWithLock(@Param("key") String key);
}
