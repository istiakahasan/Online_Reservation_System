package com.example.reservation.reservation.domain.policy;

import com.example.reservation.reservation.domain.ReservationEntity;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class FlexibleCancellationPolicy implements CancellationPolicy {

    @Override
    public boolean isCancellationAllowed(ReservationEntity reservation, Instant cancellationTime, Duration cancellationWindow) {
        if (reservation == null || reservation.getStartTime() == null) {
            return false;
        }
        Instant checkTime = cancellationTime != null ? cancellationTime : Instant.now();
        return checkTime.isBefore(reservation.getStartTime());
    }
}
