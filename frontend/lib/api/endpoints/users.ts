import { apiClient } from "../client.ts";
import type { UserResponse, UpdateUserRequest } from "../types.ts";

export const usersApi = {
  /**
   * Retrieves user details by UUID (authenticated).
   */
  async getById(id: string): Promise<UserResponse> {
    return apiClient.get<UserResponse>(`/api/v1/users/${id}`);
  },

  /**
   * Retrieves all users belonging to a tenant business (authenticated).
   */
  async listByBusiness(businessId: string): Promise<UserResponse[]> {
    return apiClient.get<UserResponse[]>("/api/v1/users", {
      params: { businessId },
    });
  },

  /**
   * Updates profile attributes for an existing user (authenticated).
   */
  async update(id: string, request: UpdateUserRequest): Promise<UserResponse> {
    return apiClient.put<UserResponse>(`/api/v1/users/${id}`, request);
  },
};
