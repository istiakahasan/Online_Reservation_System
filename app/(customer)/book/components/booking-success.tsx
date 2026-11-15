"use client";

import React from "react";
import { useBooking } from "../booking-context.tsx";

export function BookingSuccess() {
  const {
    confirmedReservation,
    selectedService,
    selectedStaff,
    isAnyStaff,
    businessName,
    businessTimezone,
    customerInfo,
    resetWizard,
  } = useBooking();

  if (!confirmedReservation) return null;

  const formatUtcDate = (isoString?: string) => {
    if (!isoString) return "";
    try {
      const d = new Date(isoString);
      return d.toLocaleDateString("en-US", {
        weekday: "long",
        year: "numeric",
        month: "long",
        day: "numeric",
      });
    } catch {
      return isoString;
    }
  };

  const formatUtcTime = (isoString?: string) => {
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

  return (
    <section className="booking-success-container" aria-labelledby="success-heading">
      <div className="success-icon-badge">✓</div>

      <h3 id="success-heading" className="success-title">
        Reservation Confirmed!
      </h3>
      <p className="success-subtitle">
        Your appointment at <strong>{businessName}</strong> has been successfully booked.
        A confirmation email has been dispatched to <strong>{customerInfo.email}</strong>.
      </p>

      <div className="receipt-card">
        <div className="receipt-header">
          <span className="receipt-label">Booking Reference</span>
          <code className="receipt-code" data-testid="booking-reference-id">
            {confirmedReservation.id}
          </code>
        </div>

        <div className="receipt-grid">
          <div className="receipt-item">
            <span className="receipt-item-label">Service</span>
            <span className="receipt-item-value">{selectedService?.name}</span>
          </div>

          <div className="receipt-item">
            <span className="receipt-item-label">Specialist</span>
            <span className="receipt-item-value">
              {isAnyStaff ? "Assigned Team Specialist" : selectedStaff?.fullName}
            </span>
          </div>

          <div className="receipt-item">
            <span className="receipt-item-label">Date & Time</span>
            <span className="receipt-item-value">
              {formatUtcDate(confirmedReservation.startTime)} at{" "}
              {formatUtcTime(confirmedReservation.startTime)}
            </span>
          </div>

          <div className="receipt-item">
            <span className="receipt-item-label">Operating Timezone</span>
            <span className="receipt-item-value">{businessTimezone}</span>
          </div>

          <div className="receipt-item">
            <span className="receipt-item-label">Status</span>
            <span className="badge-confirmed">{confirmedReservation.status}</span>
          </div>

          <div className="receipt-item">
            <span className="receipt-item-label">Client Name</span>
            <span className="receipt-item-value">
              {customerInfo.firstName} {customerInfo.lastName}
            </span>
          </div>
        </div>
      </div>

      <div className="success-actions">
        <button
          type="button"
          className="wizard-primary-button"
          onClick={resetWizard}
          data-testid="book-another-button"
        >
          Book Another Appointment
        </button>
      </div>
    </section>
  );
}
