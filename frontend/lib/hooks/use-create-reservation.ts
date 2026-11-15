import { useMutation, useQueryClient } from "@tanstack/react-query";
import { reservationsApi } from "../api/endpoints/reservations.ts";
import { ApiClientError } from "../api/client.ts";
import type {
  CreateReservationRequest,
  ReservationResponse,
  TimeSlotResponse,
} from "../api/types.ts";

export interface CreateReservationMutationContext {
  previousSlots?: TimeSlotResponse[];
  availabilityQueryKey?: unknown[];
}

export function useCreateReservation() {
  const queryClient = useQueryClient();

  return useMutation<
    ReservationResponse,
    ApiClientError | Error,
    CreateReservationRequest,
    CreateReservationMutationContext
  >({
    mutationFn: async (request: CreateReservationRequest) => {
      return reservationsApi.createReservation(request);
    },

    // Optimistic Update Before Mutation Executes
    onMutate: async (newReservation) => {
      const datePart = newReservation.startTime.split("T")[0];
      const availabilityQueryKey = [
        "availability",
        newReservation.businessId,
        newReservation.serviceId,
        newReservation.staffId,
        datePart,
      ];

      // 1. Cancel any outgoing queries so they don't overwrite optimistic update
      await queryClient.cancelQueries({ queryKey: availabilityQueryKey });

      // 2. Snapshot the previous slots for rollback
      const previousSlots = queryClient.getQueryData<TimeSlotResponse[]>(availabilityQueryKey);

      // 3. Optimistically mark the chosen slot as unavailable in the client cache
      if (previousSlots) {
        queryClient.setQueryData<TimeSlotResponse[]>(availabilityQueryKey, (oldSlots) => {
          if (!oldSlots) return [];
          return oldSlots.map((slot) => {
            if (slot.startInstant === newReservation.startTime) {
              return { ...slot, available: false };
            }
            return slot;
          });
        });
      }

      // Return context containing snapshot for rollback
      return { previousSlots, availabilityQueryKey };
    },

    // Rollback On Error
    onError: (error, _newReservation, context) => {
      if (context?.availabilityQueryKey && context?.previousSlots) {
        // Rollback cache to snapshot
        queryClient.setQueryData(context.availabilityQueryKey, context.previousSlots);
      }

      // If HTTP 409 Conflict, force an immediate background refetch from backend
      if (error instanceof ApiClientError && error.status === 409) {
        if (context?.availabilityQueryKey) {
          queryClient.invalidateQueries({ queryKey: context.availabilityQueryKey });
        }
      }
    },

    // Always refetch on settlement to ensure server state is authoritative
    onSettled: (_data, _error, newReservation) => {
      queryClient.invalidateQueries({ queryKey: ["availability"] });
      if (newReservation?.customerId) {
        queryClient.invalidateQueries({
          queryKey: ["reservations", newReservation.customerId],
        });
      }
    },
  });
}
