import { apiClient } from "../client.ts";
import type { CreateServiceRequest, ServiceResponse } from "../types.ts";

export const servicesApi = {
  /**
   * Adds a new service offering to a business catalog (authenticated).
   */
  async create(request: CreateServiceRequest): Promise<ServiceResponse> {
    return apiClient.post<ServiceResponse>("/api/v1/services", request);
  },

  /**
   * Retrieves service details by UUID.
   */
  async getById(id: string): Promise<ServiceResponse> {
    return apiClient.get<ServiceResponse>(`/api/v1/services/${id}`, {
      skipAuth: true,
    });
  },

  /**
   * Retrieves all active offerings for a business tenant.
   */
  async listActive(businessId: string): Promise<ServiceResponse[]> {
    return apiClient.get<ServiceResponse[]>("/api/v1/services", {
      params: { businessId },
      skipAuth: true,
    });
  },
};
