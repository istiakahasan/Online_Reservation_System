"use client";

import React from "react";
import { useBooking } from "../booking-context.tsx";
import { useServices } from "../../../lib/hooks/use-services.ts";
import { ServiceListSkeleton } from "./loading-skeleton.tsx";
import { ErrorAlert } from "./error-alert.tsx";
import type { ServiceResponse } from "../../../lib/api/types.ts";

export function StepService() {
  const { businessId, selectedService, selectService, nextStep } = useBooking();
  const { data: services, isLoading, error, refetch } = useServices(businessId);

  const formatPrice = (amount?: number, currency = "USD") => {
    if (amount === undefined || amount === null) return "Free";
    if (amount === 0) return "Free";
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency,
    }).format(amount);
  };

  const handleSelect = (service: ServiceResponse) => {
    selectService(service);
  };

  if (isLoading) {
    return <ServiceListSkeleton />;
  }

  if (error) {
    return (
      <ErrorAlert
        title="Unable to load services"
        message={error.message || "Failed to load treatment catalog. Please try again."}
        onRetry={() => refetch()}
      />
    );
  }

  if (!services || services.length === 0) {
    return (
      <div className="empty-state-card" role="status">
        <div className="empty-icon">💆</div>
        <h3>No services currently available</h3>
        <p>This business does not have any active bookable offerings right now.</p>
      </div>
    );
  }

  return (
    <section className="step-section" aria-labelledby="step-service-heading">
      <div className="step-header">
        <h3 id="step-service-heading" className="step-main-title">
          1. Select a Service
        </h3>
        <p className="step-subtitle">
          Choose a treatment or service package from our offerings below.
        </p>
      </div>

      <div
        role="radiogroup"
        aria-label="Available services"
        className="service-cards-grid"
      >
        {services.map((service) => {
          const isSelected = selectedService?.id === service.id;

          return (
            <div
              key={service.id}
              role="radio"
              aria-checked={isSelected}
              tabIndex={0}
              className={`selection-card ${isSelected ? "selected" : ""}`}
              onClick={() => handleSelect(service)}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  e.preventDefault();
                  handleSelect(service);
                }
              }}
              data-testid={`service-card-${service.id}`}
            >
              <div className="selection-radio-indicator">
                <span className="radio-circle">{isSelected ? "●" : "○"}</span>
              </div>

              <div className="selection-body">
                <div className="selection-top-row">
                  <h4 className="selection-title">{service.name}</h4>
                  <span className="selection-price" data-testid="service-price">
                    {formatPrice(service.priceAmount, service.currency)}
                  </span>
                </div>

                {service.description && (
                  <p className="selection-description">{service.description}</p>
                )}

                <div className="selection-tags">
                  <span className="tag-duration">
                    ⏱ {service.durationMinutes} minutes
                  </span>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <div className="step-actions">
        <button
          type="button"
          className="wizard-primary-button"
          disabled={!selectedService}
          onClick={nextStep}
          aria-label="Continue to specialist selection"
          data-testid="service-next-button"
        >
          Continue to Staff Selection →
        </button>
      </div>
    </section>
  );
}

