package com.example.reservation.payment.api;

import com.example.reservation.payment.service.StripeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping({"/api/webhooks/stripe", "/api/v1/webhooks/stripe"})
@RequiredArgsConstructor
@Tag(name = "Stripe Webhooks", description = "Endpoints for handling asynchronous Stripe webhook event callbacks")
public class StripeWebhookController {

    private final StripeService stripeService;

    @PostMapping
    @Operation(summary = "Process Stripe Webhook", description = "Verifies cryptographic signature and idempotently handles payment_intent events")
    @ApiResponse(responseCode = "200", description = "Webhook received and processed or skipped as duplicate")
    @ApiResponse(responseCode = "400", description = "Missing or invalid Stripe-Signature header")
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader
    ) {
        stripeService.processWebhookEvent(payload, sigHeader);
        return ResponseEntity.ok(Map.of("received", true));
    }
}
