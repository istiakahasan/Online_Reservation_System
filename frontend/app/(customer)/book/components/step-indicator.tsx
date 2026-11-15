"use client";

import React from "react";
import { useBooking } from "../booking-context.tsx";

interface StepConfig {
  number: 1 | 2 | 3 | 4;
  title: string;
  description: string;
}

const STEPS: StepConfig[] = [
  { number: 1, title: "Service", description: "Select treatment" },
  { number: 2, title: "Staff", description: "Choose specialist" },
  { number: 3, title: "Date & Time", description: "Select appointment slot" },
  { number: 4, title: "Confirm & Pay", description: "Review and reserve" },
];

export function StepIndicator() {
  const { step, setStep, selectedService, selectedStaff, isAnyStaff, selectedSlot } = useBooking();

  // Determine if a step is clickable (only if previous prerequisites are fulfilled)
  const canNavigateTo = (targetStep: 1 | 2 | 3 | 4): boolean => {
    if (targetStep === 1) return true;
    if (targetStep === 2) return Boolean(selectedService);
    if (targetStep === 3) return Boolean(selectedService && (selectedStaff || isAnyStaff));
    if (targetStep === 4) return Boolean(selectedService && (selectedStaff || isAnyStaff) && selectedSlot);
    return false;
  };

  return (
    <nav aria-label="Booking Progress" className="wizard-step-indicator">
      {/* Mobile step progress summary */}
      <div className="mobile-step-summary" aria-live="polite">
        <span className="step-badge">Step {step} of 4</span>
        <h2 className="step-current-title">{STEPS[step - 1].title}</h2>
      </div>

      {/* Visual Progress Bar for Mobile */}
      <div
        className="mobile-progress-bar-container"
        role="progressbar"
        aria-valuenow={step}
        aria-valuemin={1}
        aria-valuemax={4}
        aria-label={`Step ${step} of 4: ${STEPS[step - 1].title}`}
      >
        <div
          className="mobile-progress-bar-fill"
          style={{ width: `${(step / 4) * 100}%` }}
        />
      </div>

      {/* Desktop Step Nav List */}
      <ol className="desktop-step-list">
        {STEPS.map((s) => {
          const isCurrent = step === s.number;
          const isCompleted = step > s.number;
          const isClickable = canNavigateTo(s.number);

          return (
            <li
              key={s.number}
              className={`step-item ${isCurrent ? "current" : ""} ${
                isCompleted ? "completed" : ""
              } ${isClickable ? "clickable" : "disabled"}`}
              aria-current={isCurrent ? "step" : undefined}
            >
              <button
                type="button"
                className="step-button"
                disabled={!isClickable}
                onClick={() => isClickable && setStep(s.number)}
                aria-label={`Go to step ${s.number}: ${s.title}`}
              >
                <span className="step-circle">
                  {isCompleted ? "✓" : s.number}
                </span>
                <span className="step-labels">
                  <span className="step-title">{s.title}</span>
                  <span className="step-desc">{s.description}</span>
                </span>
              </button>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
