import { apiClient } from "../client.ts";
import type { TimeSlotResponse } from "../types.ts";

export interface GetAvailabilityParams {
  businessId: string;
  serviceId: string;
  staffId: string;
  date: string; // YYYY-MM-DD
}

export const availabilityApi = {
  /**
   * Derives open slots from working hours minus time-off minus active bookings.
   */
  async getAvailability(params: GetAvailabilityParams): Promise<TimeSlotResponse[]> {
    const { businessId, ...queryParams } = params;
    return apiClient.get<TimeSlotResponse[]>(
      `/api/businesses/${businessId}/availability`,
      {
        params: queryParams,
        skipAuth: true, // Availability is public for customer booking
      }
    );
  },
};
