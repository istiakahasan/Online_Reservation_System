package com.example.reservation.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.reservation.business.domain.BusinessEntity;
import com.example.reservation.payment.api.dto.PaymentIntentResponseDto;
import com.example.reservation.payment.domain.PaymentEntity;
import com.example.reservation.payment.domain.StripeEventEntity;
import com.example.reservation.payment.domain.event.PaymentFailedEvent;
import com.example.reservation.payment.domain.exception.InvalidWebhookSignatureException;
import com.example.reservation.payment.repo.PaymentRepository;
import com.example.reservation.payment.repo.StripeEventRepository;
import com.example.reservation.reservation.domain.ReservationEntity;
import com.example.reservation.reservation.domain.ReservationStatus;
import com.example.reservation.reservation.domain.event.ReservationConfirmed;
import com.example.reservation.reservation.domain.exception.SlotAlreadyBookedException;
import com.example.reservation.reservation.repo.ReservationRepository;
import com.example.reservation.service.domain.ServiceEntity;
import com.example.reservation.shared.observability.ReservationMetrics;
import com.example.reservation.user.domain.UserEntity;
import com.example.reservation.user.domain.UserRole;
import com.stripe.net.Webhook;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class StripeServiceImplTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private StripeEventRepository stripeEventRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ReservationMetrics reservationMetrics;

    private StripeServiceImpl stripeService;

    private static final String TEST_WEBHOOK_SECRET = "whsec_test_secret_for_unit_tests_123456";

    private BusinessEntity business;
    private UserEntity customer;
    private UserEntity staff;
    private ServiceEntity service;
    private ReservationEntity reservation;

    private UUID reservationId;

    @BeforeEach
    void setUp() {
        stripeService = new StripeServiceImpl(
                reservationRepository,
                paymentRepository,
                stripeEventRepository,
                eventPublisher,
                reservationMetrics,
                "mock-api-key",
                TEST_WEBHOOK_SECRET
        );

        reservationId = UUID.randomUUID();

        business = new BusinessEntity("Yoga Zen", "yoga-zen", "UTC", "contact@zen.com", "+1-555-0150");
        business.setId(UUID.randomUUID());

        customer = new UserEntity(business, "Sara", "Connor", "sara@example.com", "hash", "+1-555-0151", UserRole.CUSTOMER);
        customer.setId(UUID.randomUUID());

        staff = new UserEntity(business, "Instructor John", "Smith", "john@example.com", "hash", "+1-555-0152", UserRole.STAFF);
        staff.setId(UUID.randomUUID());

        service = new ServiceEntity(business, "Private Yoga Session", "60 min", 60, 0, new BigDecimal("95.00"), "USD");
        service.setId(UUID.randomUUID());

        reservation = new ReservationEntity(
                business, customer, service, staff,
                Instant.now().plus(2, ChronoUnit.DAYS),
                Instant.now().plus(2, ChronoUnit.DAYS).plus(60, ChronoUnit.MINUTES),
                new BigDecimal("95.00"), "USD",
                Instant.now().plus(10, ChronoUnit.MINUTES),
                "First session"
        );
        reservation.setId(reservationId);
        reservation.setStatus(ReservationStatus.PENDING);
    }

    private String generateValidStripeSignature(String payload, String secret) {
        long timestamp = Instant.now().getEpochSecond();
        try {
            String signature = Webhook.Util.computeHmacSha256(secret, timestamp + "." + payload);
            return "t=" + timestamp + ",v1=" + signature;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate test Stripe signature", e);
        }
    }

    @Test
    @DisplayName("createPaymentIntent: Successfully creates PaymentIntent and returns clientSecret")
    void createPaymentIntent_Success() {
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(paymentRepository.findByReservationId(reservationId)).thenReturn(Optional.empty());

        PaymentIntentResponseDto response = stripeService.createPaymentIntent(reservationId);

        assertThat(response).isNotNull();
        assertThat(response.reservationId()).isEqualTo(reservationId);
        assertThat(response.amount()).isEqualTo(new BigDecimal("95.00"));
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.clientSecret()).isNotBlank();
        assertThat(response.paymentIntentId()).startsWith("pi_mock_");

        verify(paymentRepository).saveAndFlush(any(PaymentEntity.class));
    }

    @Test
    @DisplayName("createPaymentIntent: Throws SlotAlreadyBookedException when reservation hold has expired")
    void createPaymentIntent_ExpiredHold_ThrowsSlotAlreadyBookedException() {
        reservation.setHoldExpiresAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> stripeService.createPaymentIntent(reservationId))
                .isInstanceOf(SlotAlreadyBookedException.class)
                .hasMessageContaining("Reservation hold has expired");

        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("processWebhookEvent: Valid payment_intent.succeeded confirms reservation and publishes event")
    void processWebhookEvent_ValidSucceeded_ConfirmsReservationAndPublishesEvent() {
        String paymentIntentId = "pi_3MtwxZ2eZvKYlo2C0XXXXX";
        String eventId = "evt_1MtwxZ2eZvKYlo2C0XXXXX";

        String payload = """
        {
          "id": "%s",
          "object": "event",
          "api_version": "2024-06-20",
          "type": "payment_intent.succeeded",
          "data": {
            "object": {
              "id": "%s",
              "object": "payment_intent",
              "amount": 9500,
              "currency": "usd",
              "status": "succeeded",
              "metadata": {
                "reservationId": "%s"
              }
            }
          }
        }
        """.formatted(eventId, paymentIntentId, reservationId);

        String sigHeader = generateValidStripeSignature(payload, TEST_WEBHOOK_SECRET);

        when(stripeEventRepository.existsByEventId(eventId)).thenReturn(false);

        PaymentEntity payment = new PaymentEntity(business, reservation, paymentIntentId, new BigDecimal("95.00"), "USD", "REQUIRES_PAYMENT_METHOD");
        payment.setId(UUID.randomUUID());
        when(paymentRepository.findByStripePaymentIntentId(paymentIntentId)).thenReturn(Optional.of(payment));

        stripeService.processWebhookEvent(payload, sigHeader);

        // Assert payment status updated to SUCCEEDED
        assertThat(payment.getStatus()).isEqualTo("SUCCEEDED");
        verify(paymentRepository).saveAndFlush(payment);

        // Assert reservation transitioned to CONFIRMED
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(reservationRepository).saveAndFlush(reservation);

        // Assert ReservationConfirmed event published
        ArgumentCaptor<ReservationConfirmed> eventCaptor = ArgumentCaptor.forClass(ReservationConfirmed.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ReservationConfirmed confirmedEvent = eventCaptor.getValue();
        assertThat(confirmedEvent.reservationId()).isEqualTo(reservationId);

        // Assert event record persisted in stripe_events
        verify(stripeEventRepository).saveAndFlush(any(StripeEventEntity.class));
    }

    @Test
    @DisplayName("processWebhookEvent: Replay same event is an idempotent NO-OP (skips processing)")
    void processWebhookEvent_ReplaySameEvent_NoOp() {
        String eventId = "evt_duplicate_replay_12345";
        String payload = """
        {
          "id": "%s",
          "object": "event",
          "api_version": "2024-06-20",
          "type": "payment_intent.succeeded",
          "data": {
            "object": {
              "id": "pi_dup_123",
              "object": "payment_intent"
            }
          }
        }
        """.formatted(eventId);

        String sigHeader = generateValidStripeSignature(payload, TEST_WEBHOOK_SECRET);

        // Already exists in stripe_events table!
        when(stripeEventRepository.existsByEventId(eventId)).thenReturn(true);

        stripeService.processWebhookEvent(payload, sigHeader);

        // PROOF OF IDEMPOTENT NO-OP:
        // No reservation or payment lookup, no state change, no event emission
        verify(paymentRepository, never()).findByStripePaymentIntentId(any());
        verify(reservationRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("processWebhookEvent: Throws InvalidWebhookSignatureException on bad signature (returns 400)")
    void processWebhookEvent_BadSignature_ThrowsInvalidWebhookSignatureException() {
        String payload = "{\"id\":\"evt_bad\",\"type\":\"payment_intent.succeeded\"}";
        String badSigHeader = "t=1700000000,v1=tampered_and_invalid_signature";

        assertThatThrownBy(() -> stripeService.processWebhookEvent(payload, badSigHeader))
                .isInstanceOf(InvalidWebhookSignatureException.class)
                .hasMessageContaining("Stripe signature verification failed");

        verify(stripeEventRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("processWebhookEvent: payment_intent.payment_failed reverts to PENDING and publishes PaymentFailedEvent")
    void processWebhookEvent_PaymentFailed_RevertsToPendingAndPublishesFailedEvent() {
        String paymentIntentId = "pi_failed_12345";
        String eventId = "evt_failed_12345";

        String payload = """
        {
          "id": "%s",
          "object": "event",
          "api_version": "2024-06-20",
          "type": "payment_intent.payment_failed",
          "data": {
            "object": {
              "id": "%s",
              "object": "payment_intent",
              "amount": 9500,
              "currency": "usd",
              "status": "requires_payment_method",
              "last_payment_error": {
                "message": "Insufficient funds"
              },
              "metadata": {
                "reservationId": "%s"
              }
            }
          }
        }
        """.formatted(eventId, paymentIntentId, reservationId);

        String sigHeader = generateValidStripeSignature(payload, TEST_WEBHOOK_SECRET);

        when(stripeEventRepository.existsByEventId(eventId)).thenReturn(false);

        PaymentEntity payment = new PaymentEntity(business, reservation, paymentIntentId, new BigDecimal("95.00"), "USD", "REQUIRES_PAYMENT_METHOD");
        payment.setId(UUID.randomUUID());
        when(paymentRepository.findByStripePaymentIntentId(paymentIntentId)).thenReturn(Optional.of(payment));

        stripeService.processWebhookEvent(payload, sigHeader);

        // Payment status marked as FAILED
        assertThat(payment.getStatus()).isEqualTo("FAILED");
        verify(paymentRepository).saveAndFlush(payment);

        // Reservation maintained/reverted to PENDING
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        verify(reservationRepository).saveAndFlush(reservation);

        // PaymentFailedEvent published to notify customer
        ArgumentCaptor<PaymentFailedEvent> eventCaptor = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        PaymentFailedEvent failedEvent = eventCaptor.getValue();
        assertThat(failedEvent.reservationId()).isEqualTo(reservationId);
        assertThat(failedEvent.failureMessage()).contains("Insufficient funds");
    }
}
