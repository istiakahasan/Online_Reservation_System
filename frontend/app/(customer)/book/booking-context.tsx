"use client";

import React, { createContext, useContext, useState, useMemo, type ReactNode } from "react";
import type {
  ServiceResponse,
  StaffResponse,
  TimeSlotResponse,
  ReservationResponse,
} from "../../../lib/api/types.ts";

export interface CustomerInfo {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  notes: string;
}

export interface BookingState {
  step: 1 | 2 | 3 | 4;
  businessId: string;
  businessName: string;
  businessTimezone: string;
  selectedService: ServiceResponse | null;
  selectedStaff: StaffResponse | null;
  isAnyStaff: boolean;
  selectedDate: string; // YYYY-MM-DD
  selectedSlot: TimeSlotResponse | null;
  customerInfo: CustomerInfo;
  confirmedReservation: ReservationResponse | null;
}

export interface BookingContextValue extends BookingState {
  setStep: (step: 1 | 2 | 3 | 4) => void;
  nextStep: () => void;
  prevStep: () => void;
  selectService: (service: ServiceResponse) => void;
  selectStaff: (staff: StaffResponse | null, isAny?: boolean) => void;
  selectDate: (date: string) => void;
  selectSlot: (slot: TimeSlotResponse | null) => void;
  updateCustomerInfo: (info: Partial<CustomerInfo>) => void;
  setConfirmedReservation: (reservation: ReservationResponse | null) => void;
  resetWizard: () => void;
}

const BookingContext = createContext<BookingContextValue | undefined>(undefined);

export interface BookingProviderProps {
  children: ReactNode;
  initialBusinessId: string;
  initialBusinessName?: string;
  initialTimezone?: string;
}

export function BookingProvider({
  children,
  initialBusinessId,
  initialBusinessName = "Our Salon & Wellness Center",
  initialTimezone = "America/New_York",
}: BookingProviderProps) {
  const getTodayDateString = () => new Date().toISOString().split("T")[0];

  const [step, setStep] = useState<1 | 2 | 3 | 4>(1);
  const [selectedService, setSelectedService] = useState<ServiceResponse | null>(null);
  const [selectedStaff, setSelectedStaff] = useState<StaffResponse | null>(null);
  const [isAnyStaff, setIsAnyStaff] = useState<boolean>(true);
  const [selectedDate, setSelectedDate] = useState<string>(getTodayDateString());
  const [selectedSlot, setSelectedSlot] = useState<TimeSlotResponse | null>(null);
  const [customerInfo, setCustomerInfo] = useState<CustomerInfo>({
    firstName: "",
    lastName: "",
    email: "",
    phone: "",
    notes: "",
  });
  const [confirmedReservation, setConfirmedReservation] = useState<ReservationResponse | null>(null);

  const nextStep = () => {
    setStep((current) => Math.min(current + 1, 4) as 1 | 2 | 3 | 4);
  };

  const prevStep = () => {
    setStep((current) => Math.max(current - 1, 1) as 1 | 2 | 3 | 4);
  };

  const selectService = (service: ServiceResponse) => {
    setSelectedService(service);
    setSelectedSlot(null); // Reset slot when service duration/price changes
  };

  const selectStaff = (staff: StaffResponse | null, isAny = false) => {
    setSelectedStaff(staff);
    setIsAnyStaff(isAny);
    setSelectedSlot(null); // Reset slot when staff changes
  };

  const selectDate = (date: string) => {
    setSelectedDate(date);
    setSelectedSlot(null);
  };

  const selectSlot = (slot: TimeSlotResponse | null) => {
    setSelectedSlot(slot);
  };

  const updateCustomerInfo = (info: Partial<CustomerInfo>) => {
    setCustomerInfo((prev) => ({ ...prev, ...info }));
  };

  const resetWizard = () => {
    setStep(1);
    setSelectedService(null);
    setSelectedStaff(null);
    setIsAnyStaff(true);
    setSelectedDate(getTodayDateString());
    setSelectedSlot(null);
    setConfirmedReservation(null);
  };

  const value = useMemo<BookingContextValue>(
    () => ({
      step,
      businessId: initialBusinessId,
      businessName: initialBusinessName,
      businessTimezone: initialTimezone,
      selectedService,
      selectedStaff,
      isAnyStaff,
      selectedDate,
      selectedSlot,
      customerInfo,
      confirmedReservation,
      setStep,
      nextStep,
      prevStep,
      selectService,
      selectStaff,
      selectDate,
      selectSlot,
      updateCustomerInfo,
      setConfirmedReservation,
      resetWizard,
    }),
    [
      step,
      initialBusinessId,
      initialBusinessName,
      initialTimezone,
      selectedService,
      selectedStaff,
      isAnyStaff,
      selectedDate,
      selectedSlot,
      customerInfo,
      confirmedReservation,
    ]
  );

  return <BookingContext.Provider value={value}>{children}</BookingContext.Provider>;
}

export function useBooking(): BookingContextValue {
  const context = useContext(BookingContext);
  if (!context) {
    throw new Error("useBooking must be used within a BookingProvider");
  }
  return context;
}
