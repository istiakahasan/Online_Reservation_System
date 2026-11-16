package com.example.reservation.shared.observability;

import com.example.reservation.shared.security.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servlet filter populating SLF4J MDC with requestId, tenantId, and userId
 * for every inbound HTTP request. Ensures strict cleanup on completion.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcLoggingFilter extends OncePerRequestFilter {

    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_TENANT_ID = "tenantId";
    public static final String MDC_USER_ID = "userId";

    public static final String HEADER_REQUEST_ID = "X-Request-ID";
    public static final String HEADER_CORRELATION_ID = "X-Correlation-ID";
    public static final String HEADER_TENANT_ID = "X-Tenant-ID";

    private static final Pattern BUSINESS_PATH_PATTERN =
            Pattern.compile(".*/businesses/([0-9a-fA-F-]{36}).*");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        String tenantId = resolveTenantId(request);
        String userId = resolveUserId();

        try {
            MDC.put(MDC_REQUEST_ID, requestId);
            MDC.put(MDC_TENANT_ID, tenantId);
            MDC.put(MDC_USER_ID, userId);

            // Propagate correlation id back to client response
            response.setHeader(HEADER_REQUEST_ID, requestId);

            filterChain.doFilter(request, response);
        } finally {
            // Strict cleanup prevents cross-request MDC leakage across virtual threads / thread pools
            MDC.clear();
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String headerId = request.getHeader(HEADER_REQUEST_ID);
        if (headerId == null || headerId.isBlank()) {
            headerId = request.getHeader(HEADER_CORRELATION_ID);
        }
        if (headerId != null && !headerId.isBlank()) {
            return headerId.trim();
        }
        return UUID.randomUUID().toString();
    }

    private String resolveTenantId(HttpServletRequest request) {
        // 1. From Authenticated Security Principal
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            if (user.tenantId() != null) {
                return user.tenantId().toString();
            }
        }

        // 2. From X-Tenant-ID header
        String tenantHeader = request.getHeader(HEADER_TENANT_ID);
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            return tenantHeader.trim();
        }

        // 3. From URI path if accessing business resource
        String requestUri = request.getRequestURI();
        if (requestUri != null) {
            Matcher matcher = BUSINESS_PATH_PATTERN.matcher(requestUri);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }

        // 4. From query param businessId
        String queryBusinessId = request.getParameter("businessId");
        if (queryBusinessId != null && !queryBusinessId.isBlank()) {
            return queryBusinessId.trim();
        }

        return "none";
    }

    private String resolveUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            if (user.userId() != null) {
                return user.userId().toString();
            }
            if (user.getUsername() != null) {
                return user.getUsername();
            }
        }
        return "anonymous";
    }
}

