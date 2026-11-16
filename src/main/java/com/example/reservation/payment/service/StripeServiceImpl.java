package com.example.reservation.payment.service;

import com.example.reservation.payment.api.dto.PaymentIntentResponseDto;
import com.example.reservation.payment.domain.PaymentEntity;
import com.example.reservation.payment.domain.StripeEventEntity;
import com.example.reservation.payment.domain.event.PaymentFailedEvent;
import com.example.reservation.payment.domain.exception.InvalidWebhookSignatureException;
import com.example.reservation.payment.repo.PaymentRepository;
import com.example.reservation.payment.repo.StripeEventRepository;
import com.example.reservation.reservation.domain.ReservationEntity;
import com.example.reservation.reservation.domain.ReservationStateMachine;
import com.example.reservation.reservation.domain.ReservationStatus;
import com.example.reservation.reservation.domain.event.ReservationConfirmed;
import com.example.reservation.reservation.domain.exception.SlotAlreadyBookedException;
import com.example.reservation.reservation.repo.ReservationRepository;
import com.example.reservation.shared.exception.AppException;
import com.example.reservation.shared.observability.ReservationMetrics;
import com.example.reservation.user.domain.UserRole;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.net.EventGSON;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class StripeServiceImpl implements StripeService {

    public static final UUID SYSTEM_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final StripeEventRepository stripeEventRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ReservationMetrics reservationMetrics;

    private final String apiKey;
    private final String webhookSecret;

    public StripeServiceImpl(
            ReservationRepository reservationRepository,
            PaymentRepository paymentRepository,
            StripeEventRepository stripeEventRepository,
            ApplicationEventPublisher eventPublisher,
            ReservationMetrics reservationMetrics,
            @Value("${application.stripe.api-key:mock-stripe-key}") String apiKey,
            @Value("${application.stripe.webhook-secret:mock-webhook-secret}") String webhookSecret
    ) {
        this.reservationRepository = reservationRepository;
        this.paymentRepository = paymentRepository;
        this.stripeEventRepository = stripeEventRepository;
        this.eventPublisher = eventPublisher;
        this.reservationMetrics = reservationMetrics;
        this.apiKey = apiKey;
        this.webhookSecret = webhookSecret;

        if (apiKey != null && !apiKey.isBlank()) {
            Stripe.apiKey = apiKey;
        }
    }

    @Override
    public PaymentIntentResponseDto createPaymentIntent(UUID reservationId) {
        log.info("Creating Stripe PaymentIntent for reservationId={}", reservationId);

        // 1. Verify reservation state
        ReservationEntity reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new AppException("Reservation not found with ID: " + reservationId,
                        "RESERVATION_NOT_FOUND", HttpStatus.NOT_FOUND) {});

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new AppException("Cannot create PaymentIntent for reservation with status: " + reservation.getStatus(),
                    "INVALID_RESERVATION_STATUS", HttpStatus.CONFLICT) {};
        }

        if (reservation.getHoldExpiresAt() != null && reservation.getHoldExpiresAt().isBefore(Instant.now())) {
            throw new SlotAlreadyBookedException("Reservation hold has expired. Please create a new booking.");
        }

        long amountInCents = reservation.getTotalPrice().multiply(BigDecimal.valueOf(100)).longValue();
        String currency = reservation.getCurrency().toLowerCase();

        String clientSecret;
        String paymentIntentId;

        // 2. Call Stripe API outside @Transactional (CLAUDE.md Rule 12)
        if (apiKey == null || apiKey.startsWith("mock") || apiKey.isBlank()) {
            paymentIntentId = "pi_mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            clientSecret = paymentIntentId + "_secret_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            log.info("Mock Stripe PaymentIntent created: id={}, clientSecret={}", paymentIntentId, clientSecret);
        } else {
            try {
                PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                        .setAmount(amountInCents)
                        .setCurrency(currency)
                        .putMetadata("reservationId", reservation.getId().toString())
                        .putMetadata("businessId", reservation.getBusiness().getId().toString())
                        .putMetadata("customerId", reservation.getCustomer().getId().toString())
                        .setAutomaticPaymentMethods(
                                PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build()
                        )
                        .build();

                PaymentIntent intent = PaymentIntent.create(params);
                paymentIntentId = intent.getId();
                clientSecret = intent.getClientSecret();
                log.info("Stripe PaymentIntent created: id={}", paymentIntentId);
            } catch (StripeException e) {
                log.error("Failed to create Stripe PaymentIntent: {}", e.getMessage(), e);
                reservationMetrics.recordPaymentFailure(reservation.getBusiness().getId(), e.getMessage());
                throw new AppException("Stripe PaymentIntent creation failed: " + e.getMessage(),
                        "STRIPE_API_ERROR", HttpStatus.BAD_GATEWAY) {};
            }
        }

        // 3. Persist Payment record in database transaction
        savePaymentRecord(reservation, paymentIntentId, reservation.getTotalPrice(), reservation.getCurrency());

        return new PaymentIntentResponseDto(
                clientSecret,
                paymentIntentId,
                reservation.getTotalPrice(),
                reservation.getCurrency(),
                reservation.getId()
        );
    }

    @Transactional
    public void savePaymentRecord(ReservationEntity reservation, String paymentIntentId, BigDecimal amount, String currency) {
        PaymentEntity payment = paymentRepository.findByReservationId(reservation.getId())
                .orElseGet(() -> new PaymentEntity(
                        reservation.getBusiness(),
                        reservation,
                        paymentIntentId,
                        amount,
                        currency,
                        "REQUIRES_PAYMENT_METHOD"
                ));

        payment.setStripePaymentIntentId(paymentIntentId);
        payment.setAmount(amount);
        payment.setCurrency(currency);
        payment.setStatus("REQUIRES_PAYMENT_METHOD");
        paymentRepository.saveAndFlush(payment);
    }

    @Override
    public void processWebhookEvent(String payload, String sigHeader) {
        // 1. Verify signature
        if (sigHeader == null || sigHeader.isBlank()) {
            throw new InvalidWebhookSignatureException("Missing Stripe-Signature header");
        }

        Event event;
        if (webhookSecret == null || webhookSecret.isBlank() || webhookSecret.startsWith("mock")) {
            try {
                event = EventGSON.getInstance().fromJson(payload, Event.class);
                if (event == null || event.getId() == null) {
                    throw new InvalidWebhookSignatureException("Malformed event JSON payload");
                }
            } catch (Exception ex) {
                throw new InvalidWebhookSignatureException("Failed to parse Stripe event payload: " + ex.getMessage());
            }
        } else {
            try {
                event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            } catch (SignatureVerificationException e) {
                log.warn("Invalid Stripe signature: {}", e.getMessage());
                throw new InvalidWebhookSignatureException("Stripe signature verification failed: " + e.getMessage());
            }
        }

        // 2. Idempotency check via stripe_events table
        if (stripeEventRepository.existsByEventId(event.getId())) {
            log.info("Duplicate Stripe event received: eventId={}, type={}. Skipping (idempotent no-op).",
                    event.getId(), event.getType());
            return;
        }

        // 3. Process event transactionally
        handleEventPayload(event, payload);
    }

    @Transactional
    public void handleEventPayload(Event event, String rawPayload) {
        log.info("Processing Stripe event: id={}, type={}", event.getId(), event.getType());

        switch (event.getType()) {
            case "payment_intent.succeeded" -> handlePaymentIntentSucceeded(event, rawPayload);
            case "payment_intent.payment_failed" -> handlePaymentIntentFailed(event, rawPayload);
            default -> log.debug("Unhandled Stripe event type: {}", event.getType());
        }

        // Record processed event in stripe_events table
        try {
            StripeEventEntity eventRecord = new StripeEventEntity(
                    event.getId(),
                    event.getType(),
                    "PROCESSED",
                    rawPayload
            );
            stripeEventRepository.saveAndFlush(eventRecord);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Stripe event already recorded by concurrent thread: eventId={}", event.getId());
        }
    }

    private void handlePaymentIntentSucceeded(Event event, String rawPayload) {
        PaymentIntent paymentIntent = extractPaymentIntent(event, rawPayload);
        String paymentIntentId = paymentIntent.getId();

        PaymentEntity payment = findPayment(paymentIntent);
        if (payment == null) {
            log.warn("No matching payment record found for PaymentIntent: {}", paymentIntentId);
            return;
        }

        payment.setStatus("SUCCEEDED");
        paymentRepository.saveAndFlush(payment);

        ReservationEntity reservation = payment.getReservation();
        if (reservation.getStatus() == ReservationStatus.PENDING) {
            ReservationStateMachine stateMachine = ReservationStateMachine.of(reservation);
            ReservationConfirmed confirmedEvent = stateMachine.confirm(SYSTEM_ACTOR_ID, UserRole.ADMIN);

            reservation.setStatus(stateMachine.getCurrentStatus());
            reservationRepository.saveAndFlush(reservation);
            eventPublisher.publishEvent(confirmedEvent);

            log.info("Reservation [{}] CONFIRMED via Stripe webhook paymentIntentId={}",
                    reservation.getId(), paymentIntentId);
        }
    }

    private void handlePaymentIntentFailed(Event event, String rawPayload) {
        PaymentIntent paymentIntent = extractPaymentIntent(event, rawPayload);
        String paymentIntentId = paymentIntent.getId();

        PaymentEntity payment = findPayment(paymentIntent);
        if (payment == null) {
            log.warn("No matching payment record found for failed PaymentIntent: {}", paymentIntentId);
            return;
        }

        payment.setStatus("FAILED");
        paymentRepository.saveAndFlush(payment);

        ReservationEntity reservation = payment.getReservation();
        // Revert or maintain PENDING
        reservation.setStatus(ReservationStatus.PENDING);
        reservationRepository.saveAndFlush(reservation);

        String failureMsg = paymentIntent.getLastPaymentError() != null ?
                paymentIntent.getLastPaymentError().getMessage() : "Payment authentication or funds failure";

        reservationMetrics.recordPaymentFailure(reservation.getBusiness().getId(), failureMsg);

        eventPublisher.publishEvent(new PaymentFailedEvent(
                reservation.getId(),
                reservation.getBusiness().getId(),
                reservation.getCustomer().getId(),
                paymentIntentId,
                failureMsg,
                Instant.now()
        ));

        log.warn("Payment failed for reservation [{}], paymentIntentId={}. Status reverted/kept as PENDING.",
                reservation.getId(), paymentIntentId);
    }

    private PaymentEntity findPayment(PaymentIntent paymentIntent) {
        Optional<PaymentEntity> byStripeId = paymentRepository.findByStripePaymentIntentId(paymentIntent.getId());
        if (byStripeId.isPresent()) {
            return byStripeId.get();
        }

        if (paymentIntent.getMetadata() != null && paymentIntent.getMetadata().containsKey("reservationId")) {
            try {
                UUID resId = UUID.fromString(paymentIntent.getMetadata().get("reservationId"));
                return paymentRepository.findByReservationId(resId).orElse(null);
            } catch (Exception ignored) {}
        }

        return null;
    }

    private PaymentIntent extractPaymentIntent(Event event, String rawPayload) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        if (deserializer.getObject().isPresent() && deserializer.getObject().get() instanceof PaymentIntent pi) {
            return pi;
        }

        if (deserializer.getRawJson() != null) {
            return EventGSON.getInstance().fromJson(deserializer.getRawJson(), PaymentIntent.class);
        }

        // Fallback: parse from raw event data object
        return EventGSON.getInstance().fromJson(rawPayload, PaymentIntent.class);
    }
}
