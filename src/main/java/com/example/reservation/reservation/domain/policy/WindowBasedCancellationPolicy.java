package com.example.reservation.reservation.domain.policy;

import com.example.reservation.reservation.domain.ReservationEntity;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class WindowBasedCancellationPolicy implements CancellationPolicy {

    @Override
    public boolean isCancellationAllowed(ReservationEntity reservation, Instant cancellationTime, Duration cancellationWindow) {
        if (reservation == null || reservation.getStartTime() == null) {
            return false;
        }

        Instant checkTime = cancellationTime != null ? cancellationTime : Instant.now();

        if (cancellationWindow == null || cancellationWindow.isZero() || cancellationWindow.isNegative()) {
            return checkTime.isBefore(reservation.getStartTime());
        }

        Instant latestAllowedCancellationTime = reservation.getStartTime().minus(cancellationWindow);
        return !checkTime.isAfter(latestAllowedCancellationTime);
    }
}

