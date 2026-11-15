package com.example.reservation.payment.service;

import java.math.BigDecimal;

public record StripeRefundResult(
        boolean success,
        String refundId,
        BigDecimal refundedAmount,
        String currency,
        String status,
        String errorMessage
) {
    public static StripeRefundResult success(String refundId, BigDecimal amount, String currency, String status) {
        return new StripeRefundResult(true, refundId, amount, currency, status, null);
    }

    public static StripeRefundResult failure(String errorMessage) {
        return new StripeRefundResult(false, null, BigDecimal.ZERO, null, "FAILED", errorMessage);
    }
}
