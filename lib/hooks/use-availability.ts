import { useQuery } from "@tanstack/react-query";
import { availabilityApi } from "../api/endpoints/availability.ts";
import type { TimeSlotResponse } from "../api/types.ts";

export interface UseAvailabilityParams {
  businessId?: string;
  serviceId?: string;
  staffId?: string;
  date?: string; // YYYY-MM-DD
  allStaffIds?: string[];
  enabled?: boolean;
}

export function useAvailability({
  businessId,
  serviceId,
  staffId,
  date,
  allStaffIds = [],
  enabled = true,
}: UseAvailabilityParams) {
  const isAnyStaff = staffId === "any";
  const shouldFetch =
    Boolean(enabled) &&
    Boolean(businessId) &&
    Boolean(serviceId) &&
    Boolean(date) &&
    Boolean(staffId);

  return useQuery<TimeSlotResponse[], Error>({
    queryKey: ["availability", businessId, serviceId, staffId, date],
    queryFn: async () => {
      if (!businessId || !serviceId || !date || !staffId) {
        return [];
      }

      if (!isAnyStaff) {
        const slots = await availabilityApi.getAvailability({
          businessId,
          serviceId,
          staffId,
          date,
        });
        return slots.sort(
          (a, b) => new Date(a.startInstant).getTime() - new Date(b.startInstant).getTime()
        );
      }

      // If "any" staff is chosen, query availability across available staff
      const targetStaffList =
        allStaffIds.length > 0 ? allStaffIds : [staffId];

      const slotPromises = targetStaffList.map(async (stId) => {
        try {
          return await availabilityApi.getAvailability({
            businessId,
            serviceId,
            staffId: stId,
            date,
          });
        } catch {
          return [];
        }
      });

      const results = await Promise.all(slotPromises);
      const combined = results.flat();

      // De-duplicate slots by startInstant, preferring available ones
      const uniqueSlotsMap = new Map<string, TimeSlotResponse>();
      for (const slot of combined) {
        const key = slot.startInstant;
        if (!uniqueSlotsMap.has(key) || (!uniqueSlotsMap.get(key)!.available && slot.available)) {
          uniqueSlotsMap.set(key, slot);
        }
      }

      return Array.from(uniqueSlotsMap.values()).sort(
        (a, b) => new Date(a.startInstant).getTime() - new Date(b.startInstant).getTime()
      );
    },
    enabled: shouldFetch,
    staleTime: 10 * 1000, // Keep slots fresh (10s)
  });
}

