package com.example.reservation.business.domain;

import com.example.reservation.reservation.domain.policy.RefundPolicyType;
import com.example.reservation.shared.domain.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "businesses")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessEntity extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 100)
    private String slug;

    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone = "UTC";

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone", length = 50)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private BusinessStatus status = BusinessStatus.ACTIVE;

    @Column(name = "cancellation_window_hours", nullable = false)
    private Integer cancellationWindowHours = 24;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_policy_type", nullable = false, length = 50)
    private RefundPolicyType refundPolicyType = RefundPolicyType.FULL;

    public BusinessEntity(String name, String slug, String timezone, String email, String phone) {
        this.name = name;
        this.slug = slug;
        this.timezone = timezone != null ? timezone : "UTC";
        this.email = email;
        this.phone = phone;
        this.status = BusinessStatus.ACTIVE;
        this.cancellationWindowHours = 24;
        this.refundPolicyType = RefundPolicyType.FULL;
    }

    public BusinessEntity(String name, String slug, String timezone, String email, String phone, Integer cancellationWindowHours, RefundPolicyType refundPolicyType) {
        this(name, slug, timezone, email, phone);
        this.cancellationWindowHours = cancellationWindowHours != null ? cancellationWindowHours : 24;
        this.refundPolicyType = refundPolicyType != null ? refundPolicyType : RefundPolicyType.FULL;
    }

    public Duration getCancellationWindow() {
        return Duration.ofHours(cancellationWindowHours != null ? cancellationWindowHours : 24);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BusinessEntity that = (BusinessEntity) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
