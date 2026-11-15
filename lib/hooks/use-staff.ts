import { useQuery } from "@tanstack/react-query";
import { staffApi } from "../api/endpoints/staff.ts";
import type { StaffResponse } from "../api/types.ts";

export function useStaff(businessId?: string) {
  return useQuery<StaffResponse[], Error>({
    queryKey: ["staff", businessId],
    queryFn: () => {
      if (!businessId) {
        throw new Error("businessId is required to fetch staff members");
      }
      return staffApi.listActive(businessId);
    },
    enabled: Boolean(businessId),
  });
}

