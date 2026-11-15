package com.example.reservation.shared.security;

import org.slf4j.MDC;

import java.util.UUID;

public final class TenantContextHolder {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();
    private static final String MDC_TENANT_KEY = "tenantId";

    private TenantContextHolder() {}

    public static void setTenantId(UUID tenantId) {
        if (tenantId != null) {
            CURRENT_TENANT.set(tenantId);
            MDC.put(MDC_TENANT_KEY, tenantId.toString());
        } else {
            clear();
        }
    }

    public static UUID getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
        MDC.remove(MDC_TENANT_KEY);
    }
}
