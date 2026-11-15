import { apiClient } from "../client.ts";
import type { CreateBusinessRequest, BusinessResponse } from "../types.ts";

export const businessesApi = {
  /**
   * Registers a new business tenant profile.
   */
  async create(request: CreateBusinessRequest): Promise<BusinessResponse> {
    return apiClient.post<BusinessResponse>("/api/v1/businesses", request);
  },

  /**
   * Retrieves business details by UUID.
   */
  async getById(id: string): Promise<BusinessResponse> {
    return apiClient.get<BusinessResponse>(`/api/v1/businesses/${id}`, {
      skipAuth: true,
    });
  },

  /**
   * Retrieves business details by vanity URL slug.
   */
  async getBySlug(slug: string): Promise<BusinessResponse> {
    return apiClient.get<BusinessResponse>(`/api/v1/businesses/slug/${slug}`, {
      skipAuth: true,
    });
  },
};
