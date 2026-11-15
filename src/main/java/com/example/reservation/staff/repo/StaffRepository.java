package com.example.reservation.staff.repo;

import com.example.reservation.staff.domain.StaffEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffRepository extends JpaRepository<StaffEntity, UUID> {
    List<StaffEntity> findAllByBusinessId(UUID businessId);
    List<StaffEntity> findAllByBusinessIdAndIsActiveTrue(UUID businessId);
    Optional<StaffEntity> findByUserId(UUID userId);
}

