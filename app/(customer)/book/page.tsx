"use client";

import React, { Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { QueryProvider } from "../../../lib/hooks/use-query-provider.tsx";
import { BookingProvider } from "./booking-context.tsx";
import { BookingWizard } from "./booking-wizard.tsx";
import { useBusiness } from "../../../lib/hooks/use-business.ts";
import { ErrorAlert } from "./components/error-alert.tsx";
import "./booking-wizard.css";

function BookingWizardDataLoader() {
  const searchParams = useSearchParams();
  const businessIdOrSlug =
    searchParams.get("businessId") ||
    searchParams.get("slug") ||
    "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"; // Fallback default business UUID

  const { data: business, isLoading, error, refetch } = useBusiness(businessIdOrSlug);

  if (isLoading) {
    return (
      <div className="wizard-loading-spinner-container" aria-busy="true">
        <div className="spinner-animation" />
        <p>Connecting to business booking system...</p>
      </div>
    );
  }

  if (error || !business) {
    return (
      <div className="wizard-page-error-container">
        <ErrorAlert
          title="Business Not Found"
          message={
            error?.message ||
            "Unable to locate the requested business reservation profile. Please verify your booking link."
          }
          onRetry={() => refetch()}
        />
      </div>
    );
  }

  return (
    <BookingProvider
      initialBusinessId={business.id}
      initialBusinessName={business.name}
      initialTimezone={business.timezone || "America/New_York"}
    >
      <BookingWizard />
    </BookingProvider>
  );
}

export default function CustomerBookPage() {
  return (
    <QueryProvider>
      <div className="customer-booking-page-root">
        <Suspense
          fallback={
            <div className="wizard-loading-spinner-container" aria-busy="true">
              <div className="spinner-animation" />
              <p>Loading booking wizard...</p>
            </div>
          }
        >
          <BookingWizardDataLoader />
        </Suspense>
      </div>
    </QueryProvider>
  );
}
