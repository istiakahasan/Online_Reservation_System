package com.example.reservation.shared.observability;

import com.example.reservation.shared.security.AuthenticatedUser;
import com.example.reservation.user.domain.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcLoggingFilterTest {

    private MdcLoggingFilter mdcLoggingFilter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        mdcLoggingFilter = new MdcLoggingFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    @DisplayName("Should extract existing X-Request-ID and propagate to MDC and response header")
    void shouldExtractAndPropagateExistingRequestId() throws ServletException, IOException {
        String customRequestId = "client-req-999888";
        request.addHeader(MdcLoggingFilter.HEADER_REQUEST_ID, customRequestId);
        request.setRequestURI("/api/v1/services");

        AtomicReference<String> mdcRequestId = new AtomicReference<>();

        FilterChain filterChain = (req, res) -> {
            mdcRequestId.set(MDC.get(MdcLoggingFilter.MDC_REQUEST_ID));
        };

        mdcLoggingFilter.doFilter(request, response, filterChain);

        assertThat(mdcRequestId.get()).isEqualTo(customRequestId);
        assertThat(response.getHeader(MdcLoggingFilter.HEADER_REQUEST_ID)).isEqualTo(customRequestId);
        // Assert MDC is cleared after request
        assertThat(MDC.get(MdcLoggingFilter.MDC_REQUEST_ID)).isNull();
    }

    @Test
    @DisplayName("Should generate UUID requestId if header is missing")
    void shouldGenerateRequestIdWhenHeaderMissing() throws ServletException, IOException {
        request.setRequestURI("/api/v1/services");
        AtomicReference<String> mdcRequestId = new AtomicReference<>();

        FilterChain filterChain = (req, res) -> {
            mdcRequestId.set(MDC.get(MdcLoggingFilter.MDC_REQUEST_ID));
        };

        mdcLoggingFilter.doFilter(request, response, filterChain);

        assertThat(mdcRequestId.get()).isNotNull().isNotBlank();
        assertThat(UUID.fromString(mdcRequestId.get())).isNotNull();
        assertThat(response.getHeader(MdcLoggingFilter.HEADER_REQUEST_ID)).isEqualTo(mdcRequestId.get());
        assertThat(MDC.get(MdcLoggingFilter.MDC_REQUEST_ID)).isNull();
    }

    @Test
    @DisplayName("Should resolve tenantId and userId from AuthenticatedUser in SecurityContext")
    void shouldResolveTenantAndUserFromSecurityContext() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        AuthenticatedUser principal = new AuthenticatedUser(userId, tenantId, "staff@example.com", UserRole.STAFF);

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        AtomicReference<String> capturedTenant = new AtomicReference<>();
        AtomicReference<String> capturedUser = new AtomicReference<>();

        FilterChain filterChain = (req, res) -> {
            capturedTenant.set(MDC.get(MdcLoggingFilter.MDC_TENANT_ID));
            capturedUser.set(MDC.get(MdcLoggingFilter.MDC_USER_ID));
        };

        mdcLoggingFilter.doFilter(request, response, filterChain);

        assertThat(capturedTenant.get()).isEqualTo(tenantId.toString());
        assertThat(capturedUser.get()).isEqualTo(userId.toString());
        assertThat(MDC.get(MdcLoggingFilter.MDC_TENANT_ID)).isNull();
        assertThat(MDC.get(MdcLoggingFilter.MDC_USER_ID)).isNull();
    }

    @Test
    @DisplayName("Should resolve tenantId from URI path if unauthenticated")
    void shouldResolveTenantIdFromUriPath() throws ServletException, IOException {
        UUID pathBusinessId = UUID.randomUUID();
        request.setRequestURI("/api/businesses/" + pathBusinessId + "/availability");

        AtomicReference<String> capturedTenant = new AtomicReference<>();
        AtomicReference<String> capturedUser = new AtomicReference<>();

        FilterChain filterChain = (req, res) -> {
            capturedTenant.set(MDC.get(MdcLoggingFilter.MDC_TENANT_ID));
            capturedUser.set(MDC.get(MdcLoggingFilter.MDC_USER_ID));
        };

        mdcLoggingFilter.doFilter(request, response, filterChain);

        assertThat(capturedTenant.get()).isEqualTo(pathBusinessId.toString());
        assertThat(capturedUser.get()).isEqualTo("anonymous");
    }
}
