package com.example.reservation.user.repo;

import com.example.reservation.user.domain.UserEntity;
import com.example.reservation.user.domain.UserRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByBusinessIdAndEmail(UUID businessId, String email);
    List<UserEntity> findAllByBusinessId(UUID businessId);
    List<UserEntity> findAllByBusinessIdAndRole(UUID businessId, UserRole role);
}
