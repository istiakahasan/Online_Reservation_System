package com.example.reservation.payment.domain;

import com.example.reservation.shared.domain.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stripe_events")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StripeEventEntity extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true, length = 255)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "PROCESSED";

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    public StripeEventEntity(String eventId, String eventType, String status, String payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.status = status != null ? status : "PROCESSED";
        this.payload = payload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StripeEventEntity that = (StripeEventEntity) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
