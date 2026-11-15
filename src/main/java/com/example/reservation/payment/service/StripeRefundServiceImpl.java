package com.example.reservation.payment.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.param.RefundCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
public class StripeRefundServiceImpl implements StripeRefundService {

    private final String apiKey;

    public StripeRefundServiceImpl(@Value("${application.stripe.api-key:mock-stripe-key}") String apiKey) {
        this.apiKey = apiKey;
        if (apiKey != null && !apiKey.isBlank()) {
            Stripe.apiKey = apiKey;
        }
    }

    @Override
    public StripeRefundResult processRefund(String paymentIntentId, BigDecimal amount, String currency, String reason) {
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            return StripeRefundResult.failure("Invalid Stripe paymentIntentId: cannot be null or empty");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return StripeRefundResult.failure("Invalid refund amount: must be greater than zero");
        }

        long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValue();

        log.info("Initiating Stripe refund: paymentIntentId={}, amountCents={}, currency={}, reason={}",
                paymentIntentId, amountInCents, currency, reason);

        // If running in development/test mock mode without live secret key
        if (apiKey == null || apiKey.startsWith("mock") || apiKey.isBlank()) {
            String mockRefundId = "re_mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            log.info("Simulated mock Stripe refund generated: refundId={}", mockRefundId);
            return StripeRefundResult.success(mockRefundId, amount, currency, "succeeded");
        }

        try {
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .setAmount(amountInCents)
                    .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                    .build();

            Refund refund = Refund.create(params);
            log.info("Stripe refund created successfully: refundId={}, status={}", refund.getId(), refund.getStatus());

            return StripeRefundResult.success(refund.getId(), amount, currency, refund.getStatus());
        } catch (StripeException e) {
            log.error("Stripe refund failed for paymentIntentId={}: {}", paymentIntentId, e.getMessage(), e);
            return StripeRefundResult.failure(e.getMessage());
        }
    }
}

