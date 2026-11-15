"use client";

import React from "react";
import { useBooking } from "../booking-context.tsx";
import { useStaff } from "../../../lib/hooks/use-staff.ts";
import { StaffListSkeleton } from "./loading-skeleton.tsx";
import { ErrorAlert } from "./error-alert.tsx";
import type { StaffResponse } from "../../../lib/api/types.ts";

export function StepStaff() {
  const { businessId, selectedStaff, isAnyStaff, selectStaff, nextStep, prevStep } =
    useBooking();
  const { data: staffList, isLoading, error, refetch } = useStaff(businessId);

  const handleSelectAny = () => {
    selectStaff(null, true);
  };

  const handleSelectStaff = (staff: StaffResponse) => {
    selectStaff(staff, false);
  };

  if (isLoading) {
    return <StaffListSkeleton />;
  }

  if (error) {
    return (
      <ErrorAlert
        title="Unable to load team members"
        message={error.message || "Failed to retrieve staff profiles. Please try again."}
        onRetry={() => refetch()}
      />
    );
  }

  const staffMembers = staffList || [];

  return (
    <section className="step-section" aria-labelledby="step-staff-heading">
      <div className="step-header">
        <h3 id="step-staff-heading" className="step-main-title">
          2. Choose a Specialist
        </h3>
        <p className="step-subtitle">
          Select a preferred specialist or choose "Any Available" for maximum scheduling flexibility.
        </p>
      </div>

      <div
        role="radiogroup"
        aria-label="Staff selection options"
        className="staff-cards-grid"
      >
        {/* "Any Available" Staff Option */}
        <div
          role="radio"
          aria-checked={isAnyStaff}
          tabIndex={0}
          className={`selection-card staff-card ${isAnyStaff ? "selected" : ""}`}
          onClick={handleSelectAny}
          onKeyDown={(e) => {
            if (e.key === "Enter" || e.key === " ") {
              e.preventDefault();
              handleSelectAny();
            }
          }}
          data-testid="staff-card-any"
        >
          <div className="selection-radio-indicator">
            <span className="radio-circle">{isAnyStaff ? "●" : "○"}</span>
          </div>

          <div className="staff-avatar-placeholder any-avatar">
            ✨
          </div>

          <div className="selection-body">
            <h4 className="selection-title">Any Available Specialist</h4>
            <p className="selection-description">
              Recommended: Automatically assigns the first available team member for your requested time.
            </p>
          </div>
        </div>

        {/* Individual Staff Cards */}
        {staffMembers.map((staff) => {
          const isSelected = !isAnyStaff && selectedStaff?.id === staff.id;

          return (
            <div
              key={staff.id}
              role="radio"
              aria-checked={isSelected}
              tabIndex={0}
              className={`selection-card staff-card ${isSelected ? "selected" : ""}`}
              onClick={() => handleSelectStaff(staff)}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  e.preventDefault();
                  handleSelectStaff(staff);
                }
              }}
              data-testid={`staff-card-${staff.id}`}
            >
              <div className="selection-radio-indicator">
                <span className="radio-circle">{isSelected ? "●" : "○"}</span>
              </div>

              <div className="staff-avatar-placeholder">
                {staff.fullName.charAt(0).toUpperCase()}
              </div>

              <div className="selection-body">
                <div className="selection-top-row">
                  <h4 className="selection-title">{staff.fullName}</h4>
                  <span className="tag-job-title">{staff.jobTitle}</span>
                </div>

                {staff.bio && (
                  <p className="selection-description">{staff.bio}</p>
                )}
              </div>
            </div>
          );
        })}
      </div>

      <div className="step-actions dual-actions">
        <button
          type="button"
          className="wizard-secondary-button"
          onClick={prevStep}
          aria-label="Back to service selection"
        >
          ← Back to Services
        </button>

        <button
          type="button"
          className="wizard-primary-button"
          onClick={nextStep}
          aria-label="Continue to date and time selection"
          data-testid="staff-next-button"
        >
          Continue to Date & Time →
        </button>
      </div>
    </section>
  );
}
