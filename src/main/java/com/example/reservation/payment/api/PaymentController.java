package com.example.reservation.payment.api;

import com.example.reservation.payment.api.dto.CreatePaymentRequestDto;
import com.example.reservation.payment.api.dto.PaymentResponseDto;
import com.example.reservation.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Endpoints for Stripe payment intents and webhooks")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/intent")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create payment intent", description = "Initializes a Stripe PaymentIntent for a pending reservation")
    @ApiResponse(responseCode = "201", description = "Payment intent created")
    public ResponseEntity<PaymentResponseDto> createPaymentIntent(@Valid @RequestBody CreatePaymentRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.createPaymentIntent(request));
    }

    @GetMapping("/reservation/{reservationId}")
    @Operation(summary = "Get payment by reservation", description = "Retrieves payment details for a reservation")
    public ResponseEntity<PaymentResponseDto> getPaymentByReservation(@PathVariable UUID reservationId) {
        return ResponseEntity.ok(paymentService.getPaymentByReservationId(reservationId));
    }

    @PostMapping("/webhook")
    @Operation(summary = "Stripe webhook receiver", description = "Receives signed event notifications from Stripe")
    public ResponseEntity<Void> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader
    ) {
        paymentService.handleWebhookEvent(payload, sigHeader);
        return ResponseEntity.ok().build();
    }
}
