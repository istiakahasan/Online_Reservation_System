package com.example.reservation.reservation.domain.exception;

import com.example.reservation.shared.exception.AppException;
import org.springframework.http.HttpStatus;

public class SlotAlreadyBookedException extends AppException {

    public SlotAlreadyBookedException(String message) {
        super(message, "SLOT_ALREADY_BOOKED", HttpStatus.CONFLICT);
    }

    public SlotAlreadyBookedException() {
        this("The requested time slot has already been booked or is no longer available.");
    }
}
