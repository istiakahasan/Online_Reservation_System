import test, { describe, beforeEach } from "node:test";
import assert from "node:assert";
import { QueryClient } from "@tanstack/react-query";
import { reservationsApi } from "../lib/api/endpoints/reservations.ts";
import { ApiClientError } from "../lib/api/client.ts";
import type { TimeSlotResponse, CreateReservationRequest } from "../lib/api/types.ts";

describe("Booking Wizard Optimistic Updates and 409 Rollback with TanStack Query", () => {
  let queryClient: QueryClient;
  const originalCreateReservation = reservationsApi.createReservation;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });
  });

  test("should cancel incoming queries, snapshot state, optimistically mark slot unavailable, and rollback on 409 conflict", async () => {
    const businessId = "b-123";
    const serviceId = "s-123";
    const staffId = "st-123";
    const date = "2026-11-20";
    const targetSlotTime = "2026-11-20T14:00:00Z";

    const queryKey = ["availability", businessId, serviceId, staffId, date];

    // 1. Initial Cache State: Slot is open and available
    const initialSlots: TimeSlotResponse[] = [
      {
        staffId,
        startInstant: targetSlotTime,
        endInstant: "2026-11-20T15:00:00Z",
        localStartTime: "2026-11-20T09:00:00-05:00",
        localEndTime: "2026-11-20T10:00:00-05:00",
        durationMinutes: 60,
        available: true,
      },
      {
        staffId,
        startInstant: "2026-11-20T15:30:00Z",
        endInstant: "2026-11-20T16:30:00Z",
        localStartTime: "2026-11-20T10:30:00-05:00",
        localEndTime: "2026-11-20T11:30:00-05:00",
        durationMinutes: 60,
        available: true,
      },
    ];

    queryClient.setQueryData(queryKey, initialSlots);

    // Assert initial state is available
    let cached = queryClient.getQueryData<TimeSlotResponse[]>(queryKey);
    assert.strictEqual(cached?.[0].available, true);

    // Mock API throwing 409 Conflict (Slot double-booked concurrently)
    reservationsApi.createReservation = async (_req: CreateReservationRequest) => {
      throw new ApiClientError(
        409,
        "The requested slot was reserved by another user",
        "SLOT_ALREADY_BOOKED"
      );
    };

    // 2. Perform onMutate lifecycle: Cancel queries, snapshot previousSlots, optimistically update
    await queryClient.cancelQueries({ queryKey });
    const previousSlots = queryClient.getQueryData<TimeSlotResponse[]>(queryKey);

    queryClient.setQueryData<TimeSlotResponse[]>(queryKey, (old) =>
      old?.map((slot) =>
        slot.startInstant === targetSlotTime ? { ...slot, available: false } : slot
      )
    );

    // Assert optimistic state: Slot is now unavailable in TanStack Query client cache
    cached = queryClient.getQueryData<TimeSlotResponse[]>(queryKey);
    assert.strictEqual(cached?.[0].available, false);

    // 3. Mutation throws 409 Conflict: Trigger onError rollback
    let caught409 = false;
    try {
      await reservationsApi.createReservation({
        businessId,
        customerId: "c-123",
        serviceId,
        staffId,
        startTime: targetSlotTime,
      });
    } catch (err) {
      caught409 = true;
      assert.ok(err instanceof ApiClientError);
      assert.strictEqual(err.status, 409);
      assert.strictEqual(err.errorCode, "SLOT_ALREADY_BOOKED");

      // Rollback to previous snapshot
      queryClient.setQueryData(queryKey, previousSlots);
      // Invalidate queries to trigger background refetch
      queryClient.invalidateQueries({ queryKey });
    }

    assert.strictEqual(caught409, true);

    // 4. Assert Rollback: Slot availability in cache is restored back to true!
    cached = queryClient.getQueryData<TimeSlotResponse[]>(queryKey);
    assert.strictEqual(cached?.[0].available, true);

    // Restore original method
    reservationsApi.createReservation = originalCreateReservation;
  });
});
