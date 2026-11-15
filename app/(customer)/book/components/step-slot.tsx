"use client";

import React, { useState } from "react";
import { useBooking } from "../booking-context.tsx";
import { useAvailability } from "../../../lib/hooks/use-availability.ts";
import { useStaff } from "../../../lib/hooks/use-staff.ts";
import { SlotGridSkeleton } from "./loading-skeleton.tsx";
import { ErrorAlert } from "./error-alert.tsx";
import type { TimeSlotResponse } from "../../../lib/api/types.ts";

export function StepSlot() {
  const {
    businessId,
    businessTimezone,
    selectedService,
    selectedStaff,
    isAnyStaff,
    selectedDate,
    selectedSlot,
    selectDate,
    selectSlot,
    nextStep,
    prevStep,
  } = useBooking();

  const [conflictMessage, setConflictMessage] = useState<string | null>(null);

  // Fetch all staff members in case "Any" is selected to aggregate slots
  const { data: staffList } = useStaff(businessId);
  const allStaffIds = (staffList || []).map((s) => s.id);

  // Effective staff id: either the selected staff ID, or the first staff ID / "any"
  const effectiveStaffId = isAnyStaff
    ? allStaffIds.length > 0
      ? allStaffIds[0]
      : "any"
    : selectedStaff?.id;

  const {
    data: slots,
    isLoading,
    error,
    refetch,
    isFetching,
  } = useAvailability({
    businessId,
    serviceId: selectedService?.id,
    staffId: effectiveStaffId,
    date: selectedDate,
    allStaffIds,
    enabled: Boolean(selectedService?.id && effectiveStaffId),
  });

  // Generate next 7 calendar days for quick selection
  const upcomingDays = React.useMemo(() => {
    const days: Array<{ dateString: string; dayName: string; dayNumber: number }> = [];
    const today = new Date();
    for (let i = 0; i < 7; i++) {
      const d = new Date(today);
      d.setDate(today.getDate() + i);
      const dateString = d.toISOString().split("T")[0];
      const dayName = d.toLocaleDateString("en-US", { weekday: "short" });
      const dayNumber = d.getDate();
      days.push({ dateString, dayName, dayNumber });
    }
    return days;
  }, []);

  const formatSlotTime = (isoString?: string) => {
    if (!isoString) return "";
    try {
      const date = new Date(isoString);
      return date.toLocaleTimeString("en-US", {
        hour: "numeric",
        minute: "2-digit",
        hour12: true,
      });
    } catch {
      return isoString;
    }
  };

  const handleSlotClick = (slot: TimeSlotResponse) => {
    if (!slot.available) {
      setConflictMessage("This slot is already reserved. Please choose an open slot.");
      return;
    }
    setConflictMessage(null);
    selectSlot(slot);
  };

  const availableSlots = (slots || []).filter((s) => s.available);

  return (
    <section className="step-section" aria-labelledby="step-slot-heading">
      <div className="step-header">
        <h3 id="step-slot-heading" className="step-main-title">
          3. Choose Date & Time
        </h3>
        <p className="step-subtitle">
          Select an available slot for your {selectedService?.name || "appointment"}.
        </p>
      </div>

      {/* Explicit Timezone Callout */}
      <div className="timezone-banner" role="note" aria-label="Appointment Timezone">
        <span className="tz-icon">🌐</span>
        <div className="tz-details">
          <strong>Operating Timezone: {businessTimezone}</strong>
          <span>All appointment times are displayed in the business&apos;s local schedule.</span>
        </div>
      </div>

      {/* Graceful 409 Conflict Alert */}
      {conflictMessage && (
        <ErrorAlert
          severity="warning"
          title="Slot Double-Booking Prevented (409 Conflict)"
          message={conflictMessage}
          onRetry={() => {
            setConflictMessage(null);
            refetch();
          }}
        />
      )}

      {/* Quick Day Selector */}
      <div className="date-picker-section">
        <label htmlFor="calendar-custom-date" className="date-picker-label">
          Pick a Date:
        </label>
        <div className="quick-days-carousel" role="radiogroup" aria-label="Next 7 days">
          {upcomingDays.map((day) => {
            const isSelected = selectedDate === day.dateString;
            return (
              <button
                key={day.dateString}
                type="button"
                role="radio"
                aria-checked={isSelected}
                className={`day-pill-button ${isSelected ? "selected" : ""}`}
                onClick={() => {
                  setConflictMessage(null);
                  selectDate(day.dateString);
                }}
                data-testid={`day-pill-${day.dateString}`}
              >
                <span className="day-name">{day.dayName}</span>
                <span className="day-number">{day.dayNumber}</span>
              </button>
            );
          })}
        </div>

        <div className="custom-date-row">
          <span className="date-input-hint">or choose specific date:</span>
          <input
            id="calendar-custom-date"
            type="date"
            className="wizard-date-input"
            value={selectedDate}
            min={upcomingDays[0]?.dateString}
            onChange={(e) => {
              if (e.target.value) {
                setConflictMessage(null);
                selectDate(e.target.value);
              }
            }}
          />
        </div>
      </div>

      {/* Slot Grid States */}
      {isLoading ? (
        <SlotGridSkeleton />
      ) : error ? (
        <ErrorAlert
          title="Unable to load availability"
          message={error.message || "Failed to derive open slots. Please try another date."}
          onRetry={() => refetch()}
        />
      ) : availableSlots.length === 0 ? (
        <div className="empty-state-card" role="status">
          <div className="empty-icon">📅</div>
          <h4>No open slots on {selectedDate}</h4>
          <p>
            All specialists are currently booked or on time-off for this date.
            Please pick another day from the calendar above.
          </p>
        </div>
      ) : (
        <div className="slots-container">
          <div className="slots-header-row">
            <h4>Available Time Slots ({availableSlots.length} open)</h4>
            {isFetching && <span className="refreshing-badge">Updating...</span>}
          </div>

          <div
            role="radiogroup"
            aria-label="Available appointment times"
            className="slots-grid"
          >
            {availableSlots.map((slot) => {
              const isSelected = selectedSlot?.startInstant === slot.startInstant;
              const formattedTime =
                formatSlotTime(slot.localStartTime || slot.startInstant);
              const endTime =
                formatSlotTime(slot.localEndTime || slot.endInstant);

              return (
                <button
                  key={slot.startInstant}
                  type="button"
                  role="radio"
                  aria-checked={isSelected}
                  className={`slot-chip-button ${isSelected ? "selected" : ""}`}
                  onClick={() => handleSlotClick(slot)}
                  aria-label={`Appointment slot starting at ${formattedTime}`}
                  data-testid={`slot-${formattedTime.replace(/[:\s]/g, "-")}`}
                >
                  <span className="slot-start-time">{formattedTime}</span>
                  <span className="slot-duration-hint">until {endTime}</span>
                </button>
              );
            })}
          </div>
        </div>
      )}

      {/* Navigation Actions */}
      <div className="step-actions dual-actions">
        <button
          type="button"
          className="wizard-secondary-button"
          onClick={prevStep}
          aria-label="Back to staff selection"
        >
          ← Back to Staff
        </button>

        <button
          type="button"
          className="wizard-primary-button"
          disabled={!selectedSlot}
          onClick={nextStep}
          aria-label="Continue to confirmation and payment"
          data-testid="slot-next-button"
        >
          Continue to Confirmation →
        </button>
      </div>
    </section>
  );
}
