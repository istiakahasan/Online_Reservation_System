package com.example.reservation.reservation.domain.policy;

import com.example.reservation.reservation.domain.ReservationEntity;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class FullRefundPolicy implements RefundPolicy {

    @Override
    public RefundPolicyType getPolicyType() {
        return RefundPolicyType.FULL;
    }

    @Override
    public RefundDecision calculateRefund(ReservationEntity reservation, BigDecimal amountPaid) {
        if (amountPaid == null || amountPaid.compareTo(BigDecimal.ZERO) <= 0) {
            return new RefundDecision(false, BigDecimal.ZERO, getPolicyType(), "No payment amount available to refund");
        }
        return new RefundDecision(true, amountPaid, getPolicyType(), "Full refund granted per business policy");
    }
}
