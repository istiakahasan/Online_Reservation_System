package com.example.reservation.shared.observability;

import com.example.reservation.reservation.domain.ReservationStatus;
import com.example.reservation.reservation.repo.ReservationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Centralized Micrometer Metrics Publisher for Reservation and Payment domain operations.
 *
 * Exposes:
 * - Counter: booking_success_total
 * - Counter: booking_conflict_total
 * - Counter: payment_failure_total
 * - Timer: availability_latency
 * - Gauge: active_reservations
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationMetrics {

    private final MeterRegistry meterRegistry;
    private final ReservationRepository reservationRepository;

    private static final String UNKNOWN_TAG = "unknown";

    @PostConstruct
    public void init() {
        // Register Dynamic Active Reservations Gauge (PENDING + CONFIRMED)
        Gauge.builder("active_reservations", this, ReservationMetrics::getActiveReservationsCount)
                .description("Number of currently active reservations in PENDING or CONFIRMED state")
                .baseUnit("reservations")
                .register(meterRegistry);

        log.info("Initialized observability metrics: active_reservations gauge registered");
    }

    /**
     * Increments booking_success_total on successful hold or confirmation.
     */
    public void recordBookingSuccess(UUID businessId, UUID serviceId) {
        String tenantTag = businessId != null ? businessId.toString() : UNKNOWN_TAG;
        String serviceTag = serviceId != null ? serviceId.toString() : UNKNOWN_TAG;

        Counter.builder("booking_success_total")
                .description("Total number of successfully created reservations")
                .tag("tenant_id", tenantTag)
                .tag("service_id", serviceTag)
                .register(meterRegistry)
                .increment();

        log.debug("Metric recorded: booking_success_total [tenant={}, service={}]", tenantTag, serviceTag);
    }

    /**
     * Increments booking_conflict_total on slot collision, double booking, or optimistic lock failure.
     */
    public void recordBookingConflict(UUID businessId, UUID serviceId, String reason) {
        String tenantTag = businessId != null ? businessId.toString() : UNKNOWN_TAG;
        String serviceTag = serviceId != null ? serviceId.toString() : UNKNOWN_TAG;
        String reasonTag = reason != null ? reason : "SLOT_ALREADY_BOOKED";

        Counter.builder("booking_conflict_total")
                .description("Total number of rejected booking attempts due to double-booking or concurrency collisions")
                .tag("tenant_id", tenantTag)
                .tag("service_id", serviceTag)
                .tag("reason", reasonTag)
                .register(meterRegistry)
                .increment();

        log.warn("Metric recorded: booking_conflict_total [tenant={}, service={}, reason={}]", tenantTag, serviceTag, reasonTag);
    }

    /**
     * Increments payment_failure_total on Stripe webhook payment failure or client rejection.
     */
    public void recordPaymentFailure(UUID businessId, String reason) {
        String tenantTag = businessId != null ? businessId.toString() : UNKNOWN_TAG;
        String reasonTag = reason != null ? reason : "PAYMENT_FAILED";

        Counter.builder("payment_failure_total")
                .description("Total number of failed Stripe payment transactions")
                .tag("tenant_id", tenantTag)
                .tag("reason", reasonTag)
                .register(meterRegistry)
                .increment();

        log.warn("Metric recorded: payment_failure_total [tenant={}, reason={}]", tenantTag, reasonTag);
    }

    /**
     * Times availability slot derivation execution.
     */
    public <T> T recordAvailabilityLatency(UUID businessId, Supplier<T> supplier) {
        String tenantTag = businessId != null ? businessId.toString() : UNKNOWN_TAG;
        Timer timer = Timer.builder("availability_latency")
                .description("Execution duration of availability slot calculation engine")
                .tag("tenant_id", tenantTag)
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);

        long startTime = System.nanoTime();
        try {
            return supplier.get();
        } finally {
            long durationNanos = System.nanoTime() - startTime;
            timer.record(durationNanos, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * Records explicit availability calculation duration.
     */
    public void recordAvailabilityDuration(UUID businessId, Duration duration) {
        String tenantTag = businessId != null ? businessId.toString() : UNKNOWN_TAG;
        Timer.builder("availability_latency")
                .description("Execution duration of availability slot calculation engine")
                .tag("tenant_id", tenantTag)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
                .record(duration);
    }

    /**
     * Computes the current number of active reservations (PENDING and CONFIRMED).
     */
    public double getActiveReservationsCount() {
        try {
            return (double) reservationRepository.countByStatusIn(
                    List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED)
            );
        } catch (Exception e) {
            log.error("Failed to calculate active_reservations gauge", e);
            return 0.0;
        }
    }
}
