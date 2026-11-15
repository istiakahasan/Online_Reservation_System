"use client";

import React from "react";

export interface ErrorAlertProps {
  title?: string;
  message: string;
  onRetry?: () => void;
  severity?: "error" | "warning" | "info";
}

export function ErrorAlert({
  title = "Something went wrong",
  message,
  onRetry,
  severity = "error",
}: ErrorAlertProps) {
  return (
    <div
      role="alert"
      className={`alert-banner alert-${severity}`}
      aria-live="assertive"
    >
      <div className="alert-icon-container">
        {severity === "error" && "⚠️"}
        {severity === "warning" && "⚡"}
        {severity === "info" && "ℹ️"}
      </div>
      <div className="alert-content">
        <h4 className="alert-title">{title}</h4>
        <p className="alert-message">{message}</p>
        {onRetry && (
          <button
            type="button"
            className="alert-retry-button"
            onClick={onRetry}
            aria-label="Retry loading data"
          >
            Try Again
          </button>
        )}
      </div>
    </div>
  );
}

