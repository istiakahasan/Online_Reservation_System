import { useQuery } from "@tanstack/react-query";
import { servicesApi } from "../api/endpoints/services.ts";
import type { ServiceResponse } from "../api/types.ts";

export function useServices(businessId?: string) {
  return useQuery<ServiceResponse[], Error>({
    queryKey: ["services", businessId],
    queryFn: () => {
      if (!businessId) {
        throw new Error("businessId is required to fetch services");
      }
      return servicesApi.listActive(businessId);
    },
    enabled: Boolean(businessId),
  });
}
