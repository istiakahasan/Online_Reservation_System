package com.example.reservation.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Middleware that blocks cross-tenant reads and mutations.
 * Validates that any requested businessId / tenantId matches the JWT claim tenantId.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantGuardFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            UUID requestedTenantId = resolveRequestedTenantId(request);

            if (requestedTenantId != null && !requestedTenantId.equals(user.tenantId())) {
                log.warn("Cross-tenant access blocked! User [{}] from tenant [{}] attempted to access tenant [{}]",
                        user.userId(), user.tenantId(), requestedTenantId);

                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                Map<String, Object> errorPayload = Map.of(
                        "status", HttpStatus.FORBIDDEN.value(),
                        "errorCode", "CROSS_TENANT_ACCESS_DENIED",
                        "message", "Access denied: cross-tenant data access is strictly forbidden.",
                        "timestamp", Instant.now().toString()
                );
                response.getWriter().write(objectMapper.writeValueAsString(errorPayload));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private UUID resolveRequestedTenantId(HttpServletRequest request) {
        // 1. Check header
        String headerTenant = request.getHeader("X-Tenant-ID");
        if (StringUtils.hasText(headerTenant)) {
            try { return UUID.fromString(headerTenant); } catch (Exception ignored) {}
        }

        // 2. Check query param
        String paramTenant = request.getParameter("businessId");
        if (StringUtils.hasText(paramTenant)) {
            try { return UUID.fromString(paramTenant); } catch (Exception ignored) {}
        }

        // 3. Check URI path pattern (e.g. /api/v1/businesses/{uuid}/...)
        String uri = request.getRequestURI();
        String[] segments = uri.split("/");
        for (int i = 0; i < segments.length - 1; i++) {
            if ("businesses".equalsIgnoreCase(segments[i]) || "tenants".equalsIgnoreCase(segments[i])) {
                try {
                    return UUID.fromString(segments[i + 1]);
                } catch (Exception ignored) {}
            }
        }

        return null;
    }
}
