package com.example.reservation.payment.repo;

import com.example.reservation.payment.domain.PaymentEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    Optional<PaymentEntity> findByReservationId(UUID reservationId);
    Optional<PaymentEntity> findByStripePaymentIntentId(String paymentIntentId);
}
