package com.example.reservation.reservation.domain.policy;

import com.example.reservation.reservation.domain.ReservationEntity;

import java.math.BigDecimal;

public interface RefundPolicy {

    RefundPolicyType getPolicyType();

    RefundDecision calculateRefund(ReservationEntity reservation, BigDecimal amountPaid);
}
