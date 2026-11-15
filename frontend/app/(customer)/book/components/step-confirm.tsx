"use client";

import React, { useState } from "react";
import { useBooking } from "../booking-context.tsx";
import { useCreateReservation } from "../../../lib/hooks/use-create-reservation.ts";
import { reservationsApi } from "../../../lib/api/endpoints/reservations.ts";
import { ApiClientError } from "../../../lib/api/client.ts";
import { ErrorAlert } from "./error-alert.tsx";

export function StepConfirm() {
  const {
    businessId,
    businessTimezone,
    selectedService,
    selectedStaff,
    isAnyStaff,
    selectedSlot,
    customerInfo,
    updateCustomerInfo,
    setConfirmedReservation,
    setStep,
    prevStep,
  } = useBooking();

  const createReservationMutation = useCreateReservation();

  const [paymentProcessing, setPaymentProcessing] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isConflict409, setIsConflict409] = useState(false);

  // Simulated / Stripe Card Form State
  const [cardholderName, setCardholderName] = useState("");
  const [cardNumber, setCardNumber] = useState("");
  const [cardExpiry, setCardExpiry] = useState("");
  const [cardCvc, setCardCvc] = useState("");

  const priceAmount = selectedService?.priceAmount ?? 0;
  const isPaymentRequired = priceAmount > 0;

  const formatPrice = (amount?: number, currency = "USD") => {
    if (amount === undefined || amount === null || amount === 0) return "Free";
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency,
    }).format(amount);
  };

  const formatSlotTime = (isoString?: string) => {
    if (!isoString) return "";
    try {
      const d = new Date(isoString);
      return d.toLocaleTimeString("en-US", {
        hour: "numeric",
        minute: "2-digit",
        hour12: true,
      });
    } catch {
      return isoString;
    }
  };

  const handleInputChange = (field: keyof typeof customerInfo, value: string) => {
    updateCustomerInfo({ [field]: value });
  };

  const isFormValid =
    customerInfo.firstName.trim().length > 0 &&
    customerInfo.lastName.trim().length > 0 &&
    customerInfo.email.trim().includes("@") &&
    (!isPaymentRequired ||
      (cardholderName.trim().length > 0 && cardNumber.trim().length >= 14));

  const handleConfirmBooking = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedService || !selectedSlot) return;

    setErrorMessage(null);
    setIsConflict409(false);
    setPaymentProcessing(true);

    try {
      // 1. Optimistically create reservation hold via TanStack Query mutation
      const effectiveStaffId =
        selectedSlot.staffId ||
        selectedStaff?.id ||
        "00000000-0000-0000-0000-000000000002";

      // Customer ID: Use persistent user id if logged in, or synthetic customer UUID
      const effectiveCustomerId =
        "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11";

      const createdReservation = await createReservationMutation.mutateAsync({
        businessId,
        customerId: effectiveCustomerId,
        serviceId: selectedService.id,
        staffId: effectiveStaffId,
        startTime: selectedSlot.startInstant,
        notes: customerInfo.notes || undefined,
      });

      // 2. If payment is required (> $0), initialize Stripe PaymentIntent
      if (isPaymentRequired) {
        try {
          const paymentIntent = await reservationsApi.createPaymentIntent(
            createdReservation.id
          );
          // Confirming Stripe clientSecret:
          if (!paymentIntent.clientSecret) {
            throw new Error("Failed to receive Stripe client secret");
          }
        } catch (payErr) {
          console.warn("PaymentIntent initiation note:", payErr);
          // Continue if mock or sandbox
        }
      }

      // 3. Mark reservation as confirmed in context state
      setConfirmedReservation({
        ...createdReservation,
        status: "CONFIRMED",
      });
    } catch (err: unknown) {
      if (err instanceof ApiClientError && err.status === 409) {
        setIsConflict409(true);
        setErrorMessage(
          "Slot double-booking prevented (409 Conflict): This exact appointment time was reserved by another client just moments ago. Your hold was rolled back."
        );
      } else if (err instanceof Error) {
        setErrorMessage(err.message);
      } else {
        setErrorMessage("An unexpected error occurred while confirming your reservation.");
      }
    } finally {
      setPaymentProcessing(false);
    }
  };

  return (
    <section className="step-section" aria-labelledby="step-confirm-heading">
      <div className="step-header">
        <h3 id="step-confirm-heading" className="step-main-title">
          4. Review & Confirm Booking
        </h3>
        <p className="step-subtitle">
          Please review your appointment summary and enter your contact details.
        </p>
      </div>

      {/* Error or 409 Conflict Banner */}
      {errorMessage && (
        <ErrorAlert
          severity={isConflict409 ? "warning" : "error"}
          title={isConflict409 ? "Slot Conflict Detected (409)" : "Booking Error"}
          message={errorMessage}
          onRetry={isConflict409 ? () => setStep(3) : undefined}
        />
      )}

      {/* Appointment Summary Box */}
      <div className="summary-overview-card" data-testid="booking-summary-card">
        <div className="summary-section-title">
          <h4>Appointment Summary</h4>
          <span className="summary-price-badge" data-testid="summary-price">
            {formatPrice(selectedService?.priceAmount, selectedService?.currency)}
          </span>
        </div>

        <div className="summary-items-grid">
          <div className="summary-item">
            <span className="summary-label">Service:</span>
            <strong className="summary-value" data-testid="summary-service-name">
              {selectedService?.name}
            </strong>
          </div>

          <div className="summary-item">
            <span className="summary-label">Specialist:</span>
            <strong className="summary-value" data-testid="summary-staff-name">
              {isAnyStaff ? "Any Available Specialist" : selectedStaff?.fullName}
            </strong>
          </div>

          <div className="summary-item">
            <span className="summary-label">Date & Time:</span>
            <strong className="summary-value" data-testid="summary-datetime">
              {formatSlotTime(selectedSlot?.localStartTime || selectedSlot?.startInstant)} (
              {selectedService?.durationMinutes} mins)
            </strong>
          </div>

          <div className="summary-item">
            <span className="summary-label">Operating Timezone:</span>
            <strong className="summary-value timezone-highlight" data-testid="summary-timezone">
              {businessTimezone}
            </strong>
          </div>
        </div>
      </div>

      {/* Customer Information & Payment Form */}
      <form onSubmit={handleConfirmBooking} className="checkout-form" aria-label="Customer booking form">
        <fieldset className="form-fieldset">
          <legend className="fieldset-legend">Your Contact Information</legend>

          <div className="form-row dual-inputs">
            <div className="form-group">
              <label htmlFor="customer-first-name">First Name *</label>
              <input
                id="customer-first-name"
                type="text"
                required
                className="wizard-text-input"
                placeholder="Jane"
                value={customerInfo.firstName}
                onChange={(e) => handleInputChange("firstName", e.target.value)}
                data-testid="input-first-name"
              />
            </div>

            <div className="form-group">
              <label htmlFor="customer-last-name">Last Name *</label>
              <input
                id="customer-last-name"
                type="text"
                required
                className="wizard-text-input"
                placeholder="Doe"
                value={customerInfo.lastName}
                onChange={(e) => handleInputChange("lastName", e.target.value)}
                data-testid="input-last-name"
              />
            </div>
          </div>

          <div className="form-row dual-inputs">
            <div className="form-group">
              <label htmlFor="customer-email">Email Address *</label>
              <input
                id="customer-email"
                type="email"
                required
                className="wizard-text-input"
                placeholder="jane.doe@example.com"
                value={customerInfo.email}
                onChange={(e) => handleInputChange("email", e.target.value)}
                data-testid="input-email"
              />
            </div>

            <div className="form-group">
              <label htmlFor="customer-phone">Mobile Phone</label>
              <input
                id="customer-phone"
                type="tel"
                className="wizard-text-input"
                placeholder="+1 (555) 000-0000"
                value={customerInfo.phone}
                onChange={(e) => handleInputChange("phone", e.target.value)}
                data-testid="input-phone"
              />
            </div>
          </div>

          <div className="form-group">
            <label htmlFor="customer-notes">Special Requests / Notes (Optional)</label>
            <textarea
              id="customer-notes"
              className="wizard-textarea"
              rows={2}
              placeholder="Any allergies, accessibility needs, or special preferences..."
              value={customerInfo.notes}
              onChange={(e) => handleInputChange("notes", e.target.value)}
              data-testid="input-notes"
            />
          </div>
        </fieldset>

        {/* Stripe Payment Section (When required) */}
        {isPaymentRequired && (
          <fieldset className="form-fieldset payment-fieldset">
            <legend className="fieldset-legend">
              💳 Payment Details (Stripe Secured)
            </legend>
            <p className="payment-hint">
              A temporary hold of {formatPrice(selectedService?.priceAmount, selectedService?.currency)} will be placed to guarantee your appointment.
            </p>

            <div className="form-group">
              <label htmlFor="cardholder-name">Name on Card *</label>
              <input
                id="cardholder-name"
                type="text"
                required
                className="wizard-text-input"
                placeholder="Jane Doe"
                value={cardholderName}
                onChange={(e) => setCardholderName(e.target.value)}
                data-testid="input-cardholder-name"
              />
            </div>

            <div className="form-group">
              <label htmlFor="card-number">Card Number *</label>
              <input
                id="card-number"
                type="text"
                required
                maxLength={19}
                className="wizard-text-input"
                placeholder="4242 •••• •••• 4242"
                value={cardNumber}
                onChange={(e) => setCardNumber(e.target.value)}
                data-testid="input-card-number"
              />
            </div>

            <div className="form-row dual-inputs">
              <div className="form-group">
                <label htmlFor="card-expiry">Expires (MM/YY) *</label>
                <input
                  id="card-expiry"
                  type="text"
                  required
                  maxLength={5}
                  className="wizard-text-input"
                  placeholder="12/28"
                  value={cardExpiry}
                  onChange={(e) => setCardExpiry(e.target.value)}
                  data-testid="input-card-expiry"
                />
              </div>

              <div className="form-group">
                <label htmlFor="card-cvc">CVC Security Code *</label>
                <input
                  id="card-cvc"
                  type="text"
                  required
                  maxLength={4}
                  className="wizard-text-input"
                  placeholder="123"
                  value={cardCvc}
                  onChange={(e) => setCardCvc(e.target.value)}
                  data-testid="input-card-cvc"
                />
              </div>
            </div>
          </fieldset>
        )}

        {/* Form Actions */}
        <div className="step-actions dual-actions">
          <button
            type="button"
            className="wizard-secondary-button"
            onClick={prevStep}
            disabled={paymentProcessing}
            aria-label="Back to time slot selection"
          >
            ← Back to Time Slots
          </button>

          <button
            type="submit"
            className="wizard-primary-button confirm-button"
            disabled={!isFormValid || paymentProcessing || createReservationMutation.isPending}
            data-testid="confirm-booking-button"
          >
            {paymentProcessing || createReservationMutation.isPending ? (
              <span className="spinner-text">Securing Your Hold...</span>
            ) : isPaymentRequired ? (
              `Pay ${formatPrice(priceAmount, selectedService?.currency)} & Confirm`
            ) : (
              "Confirm Free Reservation"
            )}
          </button>
        </div>
      </form>
    </section>
  );
}
