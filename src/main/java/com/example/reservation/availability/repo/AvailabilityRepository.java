package com.example.reservation.availability.repo;

import com.example.reservation.availability.domain.AvailabilitySlotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AvailabilityRepository extends JpaRepository<AvailabilitySlotEntity, UUID> {
    List<AvailabilitySlotEntity> findAllByBusinessIdAndStaffIdAndStartTimeBetween(
            UUID businessId, UUID staffId, Instant from, Instant to
    );
}

