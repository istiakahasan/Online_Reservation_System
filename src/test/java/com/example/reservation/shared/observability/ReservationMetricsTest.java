package com.example.reservation.shared.observability;

import com.example.reservation.reservation.domain.ReservationStatus;
import com.example.reservation.reservation.repo.ReservationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationMetricsTest {

    private MeterRegistry meterRegistry;

    @Mock
    private ReservationRepository reservationRepository;

    private ReservationMetrics reservationMetrics;

    private final UUID businessId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        reservationMetrics = new ReservationMetrics(meterRegistry, reservationRepository);
        reservationMetrics.init();
    }

    @Test
    @DisplayName("Should increment booking_success_total counter on successful reservation")
    void shouldIncrementBookingSuccessCounter() {
        reservationMetrics.recordBookingSuccess(businessId, serviceId);
        reservationMetrics.recordBookingSuccess(businessId, serviceId);

        Counter counter = meterRegistry.find("booking_success_total")
                .tag("tenant_id", businessId.toString())
                .tag("service_id", serviceId.toString())
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Should increment booking_conflict_total counter on slot collision")
    void shouldIncrementBookingConflictCounter() {
        reservationMetrics.recordBookingConflict(businessId, serviceId, "SLOT_ALREADY_BOOKED");

        Counter counter = meterRegistry.find("booking_conflict_total")
                .tag("tenant_id", businessId.toString())
                .tag("service_id", serviceId.toString())
                .tag("reason", "SLOT_ALREADY_BOOKED")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should increment payment_failure_total counter on payment error")
    void shouldIncrementPaymentFailureCounter() {
        reservationMetrics.recordPaymentFailure(businessId, "card_declined");

        Counter counter = meterRegistry.find("payment_failure_total")
                .tag("tenant_id", businessId.toString())
                .tag("reason", "card_declined")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should record availability_latency timer execution duration")
    void shouldRecordAvailabilityLatencyTimer() {
        String result = reservationMetrics.recordAvailabilityLatency(businessId, () -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {}
            return "computed_slots";
        });

        assertThat(result).isEqualTo("computed_slots");

        Timer timer = meterRegistry.find("availability_latency")
                .tag("tenant_id", businessId.toString())
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isGreaterThan(5);
    }

    @Test
    @DisplayName("Should compute active_reservations gauge based on PENDING and CONFIRMED counts")
    void shouldReflectActiveReservationsInGauge() {
        when(reservationRepository.countByStatusIn(List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED)))
                .thenReturn(42L);

        Gauge gauge = meterRegistry.find("active_reservations").gauge();

        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(42.0);
    }
}

