package com.example.reservation.reservation.domain.policy;

import java.math.BigDecimal;

public record RefundDecision(
        boolean isEligible,
        BigDecimal refundAmount,
        RefundPolicyType policyType,
        String reason
) {}
