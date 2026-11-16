package com.example.reservation.payment.service;

import com.example.reservation.payment.api.dto.CreatePaymentRequestDto;
import com.example.reservation.payment.api.dto.PaymentResponseDto;
import java.util.UUID;

public interface PaymentService {
    PaymentResponseDto createPaymentIntent(CreatePaymentRequestDto request);
    PaymentResponseDto getPaymentByReservationId(UUID reservationId);
    void handleWebhookEvent(String payload, String sigHeader);
}
