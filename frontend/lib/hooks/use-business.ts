import { useQuery } from "@tanstack/react-query";
import { businessesApi } from "../api/endpoints/businesses.ts";
import type { BusinessResponse } from "../api/types.ts";

export function useBusiness(idOrSlug?: string) {
  const isUuid = idOrSlug ? /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(idOrSlug) : false;

  return useQuery<BusinessResponse, Error>({
    queryKey: ["business", idOrSlug],
    queryFn: async () => {
      if (!idOrSlug) {
        throw new Error("business ID or slug is required");
      }
      if (isUuid) {
        return businessesApi.getById(idOrSlug);
      }
      return businessesApi.getBySlug(idOrSlug);
    },
    enabled: Boolean(idOrSlug),
  });
}
