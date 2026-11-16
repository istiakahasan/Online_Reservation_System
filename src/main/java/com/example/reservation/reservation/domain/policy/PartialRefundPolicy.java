package com.example.reservation.reservation.domain.policy;

import com.example.reservation.reservation.domain.ReservationEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class PartialRefundPolicy implements RefundPolicy {

    private final BigDecimal percentage;

    public PartialRefundPolicy() {
        this(new BigDecimal("50.00"));
    }

    public PartialRefundPolicy(BigDecimal percentage) {
        this.percentage = percentage != null ? percentage : new BigDecimal("50.00");
    }

    @Override
    public RefundPolicyType getPolicyType() {
        return RefundPolicyType.PARTIAL;
    }

    @Override
    public RefundDecision calculateRefund(ReservationEntity reservation, BigDecimal amountPaid) {
        if (amountPaid == null || amountPaid.compareTo(BigDecimal.ZERO) <= 0) {
            return new RefundDecision(false, BigDecimal.ZERO, getPolicyType(), "No payment amount available to refund");
        }
        BigDecimal refundAmount = amountPaid.multiply(percentage)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        return new RefundDecision(true, refundAmount, getPolicyType(),
                String.format("Partial refund of %s%% granted per business policy", percentage.stripTrailingZeros().toPlainString()));
    }
}
