package com.example.reservation.payment.service;

import com.example.reservation.payment.api.dto.CreatePaymentRequestDto;
import com.example.reservation.payment.api.dto.PaymentResponseDto;
import com.example.reservation.payment.repo.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;

    @Override
    @Transactional
    public PaymentResponseDto createPaymentIntent(CreatePaymentRequestDto request) {
        return null;
    }

    @Override
    public PaymentResponseDto getPaymentByReservationId(UUID reservationId) {
        return null;
    }

    @Override
    @Transactional
    public void handleWebhookEvent(String payload, String sigHeader) {
    }
}
