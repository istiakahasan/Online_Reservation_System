package com.example.reservation.business.repo;

import com.example.reservation.business.domain.BusinessEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BusinessRepository extends JpaRepository<BusinessEntity, UUID> {
    Optional<BusinessEntity> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
