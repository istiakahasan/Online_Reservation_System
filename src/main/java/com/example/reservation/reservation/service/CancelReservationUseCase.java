package com.example.reservation.reservation.service;

import com.example.reservation.business.repo.BusinessRepository;
import com.example.reservation.payment.domain.PaymentEntity;
import com.example.reservation.payment.repo.PaymentRepository;
import com.example.reservation.payment.service.StripeRefundResult;
import com.example.reservation.payment.service.StripeRefundService;
import com.example.reservation.reservation.domain.ReservationEntity;
import com.example.reservation.reservation.domain.ReservationStateMachine;
import com.example.reservation.reservation.domain.ReservationStatus;
import com.example.reservation.reservation.domain.event.ReservationCancelled;
import com.example.reservation.reservation.domain.policy.CancellationPolicy;
import com.example.reservation.reservation.domain.policy.RefundDecision;
import com.example.reservation.reservation.domain.policy.RefundPolicy;
import com.example.reservation.reservation.domain.policy.RefundPolicyRegistry;
import com.example.reservation.reservation.domain.policy.RefundPolicyType;
import com.example.reservation.reservation.repo.ReservationRepository;
import com.example.reservation.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CancelReservationUseCase {

    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final CancellationPolicy cancellationPolicy;
    private final RefundPolicyRegistry refundPolicyRegistry;
    private final StripeRefundService stripeRefundService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Executes the cancellation workflow:
     * 1. Validates reservation exists & actor role.
     * 2. State machine validates the transition (e.g. CONFIRMED -> CANCELLED).
     * 3. Checks business cancellation window (e.g. >= 24h before start).
     * 4. Updates reservation state to CANCELLED in DB transaction.
     * 5. Emits ReservationCancelled domain event (consumed by notification module).
     * 6. If paid via Stripe, triggers refund outside the DB transaction (Rule 12).
     */
    public CancelReservationResult execute(CancelReservationCommand command) {
        log.info("Executing CancelReservationUseCase for reservationId={}, actorId={}, actorRole={}",
                command.reservationId(), command.actorId(), command.actorRole());

        Instant cancellationTime = command.cancellationTime() != null ? command.cancellationTime() : Instant.now();

        // 1. Validate & commit state transition in database transaction
        CancellationDataSnapshot snapshot = performDatabaseCancellation(command, cancellationTime);

        // 2. If paid via Stripe, trigger refund OUTSIDE database transaction (CLAUDE.md Rule 12)
        boolean refundTriggered = false;
        String refundId = null;
        String refundStatus = "NONE";
        BigDecimal refundAmount = snapshot.refundDecision().refundAmount();

        if (snapshot.wasPaid() && snapshot.refundDecision().isEligible() && refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            PaymentEntity payment = snapshot.payment();
            if (payment.getStripePaymentIntentId() != null && !payment.getStripePaymentIntentId().isBlank()) {
                StripeRefundResult refundResult = stripeRefundService.processRefund(
                        payment.getStripePaymentIntentId(),
                        refundAmount,
                        payment.getCurrency(),
                        command.reason()
                );

                refundTriggered = true;
                refundId = refundResult.refundId();
                refundStatus = refundResult.status();

                // 3. Persist refund result in payments table
                recordRefundResult(payment.getId(), refundResult);
            } else {
                log.warn("Payment record exists for reservationId={} but lacks stripePaymentIntentId", command.reservationId());
            }
        }

        return new CancelReservationResult(
                snapshot.reservationId(),
                ReservationStatus.CANCELLED.name(),
                true,
                snapshot.wasPaid(),
                refundTriggered,
                refundAmount,
                refundId,
                refundStatus,
                command.reason()
        );
    }

    @Transactional
    public CancellationDataSnapshot performDatabaseCancellation(CancelReservationCommand command, Instant cancellationTime) {
        ReservationEntity reservation = reservationRepository.findById(command.reservationId())
                .orElseThrow(() -> new AppException("Reservation not found with ID: " + command.reservationId(),
                        "RESERVATION_NOT_FOUND", HttpStatus.NOT_FOUND) {});

        // 1. State machine validates the transition (e.g., CONFIRMED -> CANCELLED or PENDING -> CANCELLED)
        // If already CANCELLED, throws InvalidReservationTransitionException
        ReservationStateMachine stateMachine = ReservationStateMachine.of(reservation);
        ReservationCancelled event = stateMachine.cancel(command.actorId(), command.actorRole(), command.reason());

        // 2. Check cancellation window (per business configuration)
        Duration window = reservation.getBusiness().getCancellationWindow();
        cancellationPolicy.validateCancellation(reservation, cancellationTime, window);

        // 3. Update reservation status
        reservation.setStatus(stateMachine.getCurrentStatus());
        ReservationEntity saved = reservationRepository.saveAndFlush(reservation);

        // 4. Publish event for notification module
        eventPublisher.publishEvent(event);

        // 5. Evaluate refund eligibility
        Optional<PaymentEntity> paymentOpt = paymentRepository.findByReservationId(saved.getId());
        boolean wasPaid = paymentOpt.isPresent() && "SUCCEEDED".equalsIgnoreCase(paymentOpt.get().getStatus());

        RefundDecision decision;
        if (wasPaid) {
            RefundPolicyType policyType = saved.getBusiness().getRefundPolicyType();
            RefundPolicy refundPolicy = refundPolicyRegistry.getPolicy(policyType);
            decision = refundPolicy.calculateRefund(saved, paymentOpt.get().getAmount());
        } else {
            decision = new RefundDecision(false, BigDecimal.ZERO, RefundPolicyType.NONE, "Unpaid reservation");
        }

        return new CancellationDataSnapshot(
                saved.getId(),
                wasPaid,
                paymentOpt.orElse(null),
                decision
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRefundResult(UUID paymentId, StripeRefundResult refundResult) {
        paymentRepository.findById(paymentId).ifPresent(payment -> {
            if (refundResult.success()) {
                payment.setStripeRefundId(refundResult.refundId());
                payment.setStatus("REFUNDED");
            } else {
                payment.setStatus("REFUND_FAILED");
            }
            paymentRepository.saveAndFlush(payment);
        });
    }

    public record CancellationDataSnapshot(
            UUID reservationId,
            boolean wasPaid,
            PaymentEntity payment,
            RefundDecision refundDecision
    ) {}
}
