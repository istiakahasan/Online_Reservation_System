package com.example.reservation.payment.repo;

import com.example.reservation.payment.domain.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    Optional<PaymentEntity> findByReservationId(UUID reservationId);
    Optional<PaymentEntity> findByStripePaymentIntentId(String paymentIntentId);
}
