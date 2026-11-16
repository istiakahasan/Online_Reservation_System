package com.example.reservation.reservation.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.reservation.business.domain.BusinessEntity;
import com.example.reservation.reservation.domain.ReservationEntity;
import com.example.reservation.reservation.domain.exception.CancellationWindowExpiredException;
import com.example.reservation.service.domain.ServiceEntity;
import com.example.reservation.user.domain.UserEntity;
import com.example.reservation.user.domain.UserRole;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CancellationPolicyTest {

    private final WindowBasedCancellationPolicy windowPolicy = new WindowBasedCancellationPolicy();
    private final FullRefundPolicy fullRefundPolicy = new FullRefundPolicy();
    private final PartialRefundPolicy partialRefundPolicy = new PartialRefundPolicy(new BigDecimal("50.00"));
    private final NoRefundPolicy noRefundPolicy = new NoRefundPolicy();

    private ReservationEntity createReservationWithStartTime(Instant startTime) {
        BusinessEntity business = new BusinessEntity("Test Business", "test-biz", "UTC", "test@test.com", "123", 24, RefundPolicyType.FULL);
        business.setId(UUID.randomUUID());
        UserEntity customer = new UserEntity(business, "Jane", "Doe", "jane@test.com", "pwd", "123", UserRole.CUSTOMER);
        UserEntity staff = new UserEntity(business, "Staff", "User", "staff@test.com", "pwd", "123", UserRole.STAFF);
        ServiceEntity service = new ServiceEntity(business, "Test Service", "Desc", 60, 0, new BigDecimal("100.00"), "USD");

        return new ReservationEntity(
                business,
                customer,
                service,
                staff,
                startTime,
                startTime.plus(60, ChronoUnit.MINUTES),
                new BigDecimal("100.00"),
                "USD",
                null,
                "notes"
        );
    }

    @Nested
    @DisplayName("WindowBasedCancellationPolicy Tests")
    class WindowPolicyTests {

        @Test
        @DisplayName("Cancellation allowed when request is before deadline (e.g. 48h before start with 24h window)")
        void allowedBeforeDeadline() {
            Instant startTime = Instant.now().plus(48, ChronoUnit.HOURS);
            ReservationEntity reservation = createReservationWithStartTime(startTime);
            Duration window = Duration.ofHours(24);

            boolean allowed = windowPolicy.isCancellationAllowed(reservation, Instant.now(), window);
            assertThat(allowed).isTrue();
        }

        @Test
        @DisplayName("Cancellation rejected when request is within window (e.g. 12h before start with 24h window)")
        void rejectedWithinWindow() {
            Instant startTime = Instant.now().plus(12, ChronoUnit.HOURS);
            ReservationEntity reservation = createReservationWithStartTime(startTime);
            Duration window = Duration.ofHours(24);

            boolean allowed = windowPolicy.isCancellationAllowed(reservation, Instant.now(), window);
            assertThat(allowed).isFalse();
        }

        @Test
        @DisplayName("validateCancellation throws CancellationWindowExpiredException when within window")
        void validateCancellationThrowsWhenExpired() {
            Instant startTime = Instant.now().plus(6, ChronoUnit.HOURS);
            ReservationEntity reservation = createReservationWithStartTime(startTime);
            Duration window = Duration.ofHours(24);

            assertThatThrownBy(() -> windowPolicy.validateCancellation(reservation, Instant.now(), window))
                    .isInstanceOf(CancellationWindowExpiredException.class);
        }

        @Test
        @DisplayName("Zero or negative cancellation window allows cancellation anytime before start time")
        void zeroWindowAllowsUntilStart() {
            Instant startTime = Instant.now().plus(10, ChronoUnit.MINUTES);
            ReservationEntity reservation = createReservationWithStartTime(startTime);

            boolean allowed = windowPolicy.isCancellationAllowed(reservation, Instant.now(), Duration.ZERO);
            assertThat(allowed).isTrue();
        }
    }

    @Nested
    @DisplayName("RefundPolicy Strategy Tests")
    class RefundPolicyTests {

        private final ReservationEntity dummyReservation = createReservationWithStartTime(Instant.now().plus(24, ChronoUnit.HOURS));

        @Test
        @DisplayName("FullRefundPolicy grants 100% refund for positive paid amount")
        void fullRefundGrants100Percent() {
            BigDecimal paid = new BigDecimal("150.00");
            RefundDecision decision = fullRefundPolicy.calculateRefund(dummyReservation, paid);

            assertThat(decision.isEligible()).isTrue();
            assertThat(decision.refundAmount()).isEqualByComparingTo(paid);
            assertThat(decision.policyType()).isEqualTo(RefundPolicyType.FULL);
        }

        @Test
        @DisplayName("FullRefundPolicy returns 0 refund for zero or negative amount")
        void fullRefundZeroAmount() {
            RefundDecision decision = fullRefundPolicy.calculateRefund(dummyReservation, BigDecimal.ZERO);

            assertThat(decision.isEligible()).isFalse();
            assertThat(decision.refundAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("PartialRefundPolicy grants configured 50% refund")
        void partialRefundGrants50Percent() {
            BigDecimal paid = new BigDecimal("200.00");
            RefundDecision decision = partialRefundPolicy.calculateRefund(dummyReservation, paid);

            assertThat(decision.isEligible()).isTrue();
            assertThat(decision.refundAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(decision.policyType()).isEqualTo(RefundPolicyType.PARTIAL);
        }

        @Test
        @DisplayName("NoRefundPolicy denies refund regardless of paid amount")
        void noRefundDeniesRefund() {
            BigDecimal paid = new BigDecimal("200.00");
            RefundDecision decision = noRefundPolicy.calculateRefund(dummyReservation, paid);

            assertThat(decision.isEligible()).isFalse();
            assertThat(decision.refundAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(decision.policyType()).isEqualTo(RefundPolicyType.NONE);
        }

        @Test
        @DisplayName("RefundPolicyRegistry resolves policies correctly with default fallback to FULL")
        void registryResolvesPolicies() {
            RefundPolicyRegistry registry = new RefundPolicyRegistry(List.of(
                    fullRefundPolicy,
                    partialRefundPolicy,
                    noRefundPolicy
            ));

            assertThat(registry.getPolicy(RefundPolicyType.FULL)).isSameAs(fullRefundPolicy);
            assertThat(registry.getPolicy(RefundPolicyType.PARTIAL)).isSameAs(partialRefundPolicy);
            assertThat(registry.getPolicy(RefundPolicyType.NONE)).isSameAs(noRefundPolicy);
            assertThat(registry.getPolicy(null)).isSameAs(fullRefundPolicy);
        }
    }
}
