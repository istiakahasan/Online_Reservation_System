"use client";

import React from "react";
import { useBooking } from "./booking-context.tsx";
import { StepIndicator } from "./components/step-indicator.tsx";
import { StepService } from "./components/step-service.tsx";
import { StepStaff } from "./components/step-staff.tsx";
import { StepSlot } from "./components/step-slot.tsx";
import { StepConfirm } from "./components/step-confirm.tsx";
import { BookingSuccess } from "./components/booking-success.tsx";

export function BookingWizard() {
  const { step, confirmedReservation, businessName } = useBooking();

  return (
    <div className="booking-wizard-wrapper" data-testid="booking-wizard-root">
      <header className="wizard-page-header">
        <h1 className="business-title">{businessName}</h1>
        <p className="business-tagline">Online Appointment Reservation</p>
      </header>

      {/* When booking is confirmed, display only the receipt/success screen */}
      {confirmedReservation ? (
        <BookingSuccess />
      ) : (
        <>
          <StepIndicator />

          <main className="wizard-content-card" aria-live="polite">
            {step === 1 && <StepService />}
            {step === 2 && <StepStaff />}
            {step === 3 && <StepSlot />}
            {step === 4 && <StepConfirm />}
          </main>
        </>
      )}
    </div>
  );
}
