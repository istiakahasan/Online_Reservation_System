import { apiClient } from "../client.ts";
import type { CreateStaffRequest, StaffResponse } from "../types.ts";

export const staffApi = {
  /**
   * Registers a staff member profile (authenticated).
   */
  async create(request: CreateStaffRequest): Promise<StaffResponse> {
    return apiClient.post<StaffResponse>("/api/v1/staff", request);
  },

  /**
   * Retrieves staff profile by UUID.
   */
  async getById(id: string): Promise<StaffResponse> {
    return apiClient.get<StaffResponse>(`/api/v1/staff/${id}`, {
      skipAuth: true,
    });
  },

  /**
   * Lists active staff providers for a business.
   */
  async listActive(businessId: string): Promise<StaffResponse[]> {
    return apiClient.get<StaffResponse[]>("/api/v1/staff", {
      params: { businessId },
      skipAuth: true,
    });
  },
};
