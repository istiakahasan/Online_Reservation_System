package com.example.reservation.reservation.domain.policy;

import com.example.reservation.reservation.domain.ReservationEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class NoRefundPolicy implements RefundPolicy {

    @Override
    public RefundPolicyType getPolicyType() {
        return RefundPolicyType.NONE;
    }

    @Override
    public RefundDecision calculateRefund(ReservationEntity reservation, BigDecimal amountPaid) {
        return new RefundDecision(false, BigDecimal.ZERO, getPolicyType(), "Non-refundable booking per business policy");
    }
}

