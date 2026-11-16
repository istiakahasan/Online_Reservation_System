package com.example.reservation.notification.domain;

import com.example.reservation.shared.domain.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notification_log")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationLogEntity extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "channel", nullable = false, length = 50)
    private String channel;

    @Column(name = "template", nullable = false, length = 100)
    private String template;

    @Column(name = "recipient", nullable = false)
    private String recipient;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();

    public NotificationLogEntity(UUID reservationId, String channel, String template, String recipient, String status, String errorMessage, Instant sentAt) {
        this.reservationId = reservationId;
        this.channel = channel;
        this.template = template;
        this.recipient = recipient;
        this.status = status;
        this.errorMessage = errorMessage;
        this.sentAt = sentAt != null ? sentAt : Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NotificationLogEntity that = (NotificationLogEntity) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
