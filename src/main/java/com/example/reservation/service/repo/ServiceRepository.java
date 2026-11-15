package com.example.reservation.service.repo;

import com.example.reservation.service.domain.ServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ServiceRepository extends JpaRepository<ServiceEntity, UUID> {
    List<ServiceEntity> findAllByBusinessId(UUID businessId);
    List<ServiceEntity> findAllByBusinessIdAndIsActiveTrue(UUID businessId);
}
