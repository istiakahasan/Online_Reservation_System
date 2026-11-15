"use client";

import React from "react";

export function ServiceListSkeleton() {
  return (
    <div className="skeleton-grid" aria-busy="true" aria-label="Loading available services...">
      {[1, 2, 3].map((n) => (
        <div key={n} className="skeleton-card">
          <div className="skeleton-line title" />
          <div className="skeleton-line subtitle" />
          <div className="skeleton-line text" />
          <div className="skeleton-badge-container">
            <div className="skeleton-badge" />
            <div className="skeleton-badge small" />
          </div>
        </div>
      ))}
    </div>
  );
}

export function StaffListSkeleton() {
  return (
    <div className="skeleton-grid" aria-busy="true" aria-label="Loading team specialists...">
      {[1, 2, 3].map((n) => (
        <div key={n} className="skeleton-card staff">
          <div className="skeleton-avatar" />
          <div className="skeleton-staff-info">
            <div className="skeleton-line title" />
            <div className="skeleton-line subtitle" />
          </div>
        </div>
      ))}
    </div>
  );
}

export function SlotGridSkeleton() {
  return (
    <div className="skeleton-slots-wrapper" aria-busy="true" aria-label="Loading appointment time slots...">
      <div className="skeleton-date-pills">
        {[1, 2, 3, 4, 5].map((d) => (
          <div key={d} className="skeleton-pill" />
        ))}
      </div>
      <div className="skeleton-slot-grid">
        {[1, 2, 3, 4, 5, 6, 7, 8].map((s) => (
          <div key={s} className="skeleton-slot-button" />
        ))}
      </div>
    </div>
  );
}

