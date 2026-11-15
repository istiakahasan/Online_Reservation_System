package com.example.reservation.payment.domain.exception;

import com.example.reservation.shared.exception.AppException;
import org.springframework.http.HttpStatus;

public class InvalidWebhookSignatureException extends AppException {

    public InvalidWebhookSignatureException(String message) {
        super(message, "INVALID_WEBHOOK_SIGNATURE", HttpStatus.BAD_REQUEST);
    }
}

