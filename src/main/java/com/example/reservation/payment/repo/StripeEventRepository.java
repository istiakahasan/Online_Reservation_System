package com.example.reservation.payment.repo;

import com.example.reservation.payment.domain.StripeEventEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StripeEventRepository extends JpaRepository<StripeEventEntity, UUID> {
    boolean existsByEventId(String eventId);
    Optional<StripeEventEntity> findByEventId(String eventId);
}
