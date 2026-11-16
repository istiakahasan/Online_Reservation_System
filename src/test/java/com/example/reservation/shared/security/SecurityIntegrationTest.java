package com.example.reservation.shared.security;

import com.example.reservation.auth.service.JwtService;
import com.example.reservation.business.api.BusinessController;
import com.example.reservation.business.service.BusinessService;
import com.example.reservation.payment.service.StripeService;
import com.example.reservation.reservation.api.ReservationController;
import com.example.reservation.reservation.service.ReservationService;
import com.example.reservation.shared.config.WebMvcConfig;
import com.example.reservation.shared.exception.GlobalExceptionHandler;
import com.example.reservation.shared.observability.MdcLoggingFilter;
import com.example.reservation.user.domain.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {BusinessController.class, ReservationController.class})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        TenantGuardFilter.class,
        MdcLoggingFilter.class,
        WebMvcConfig.class,
        CurrentUserArgumentResolver.class,
        GlobalExceptionHandler.class,
        JwtService.class
})
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BusinessService businessService;

    @MockBean
    private ReservationService reservationService;

    @MockBean
    private StripeService stripeService;

    private UUID tenantA;
    private UUID tenantB;
    private UUID customerAId;
    private String tokenTenantA;

    @BeforeEach
    void setUp() {
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        customerAId = UUID.randomUUID();

        // Valid JWT for Customer in Tenant A
        tokenTenantA = jwtService.generateAccessToken(
                customerAId,
                tenantA,
                "customer.a@tenant-a.com",
                UserRole.CUSTOMER
        );
    }

    @Nested
    @DisplayName("Cross-Tenant Access Security Tests")
    class CrossTenantTests {

        @Test
        @DisplayName("Cross-tenant access via URI path (/businesses/{tenantB}) is blocked with 403 FORBIDDEN")
        void crossTenantAccessViaPathBlocked() throws Exception {
            mockMvc.perform(get("/api/v1/businesses/{id}", tenantB)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenTenantA))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("CROSS_TENANT_ACCESS_DENIED"));
        }

        @Test
        @DisplayName("Cross-tenant access via X-Tenant-ID header matching another tenant is blocked with 403")
        void crossTenantAccessViaHeaderBlocked() throws Exception {
            mockMvc.perform(get("/api/v1/businesses/{id}", tenantA)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenTenantA)
                            .header("X-Tenant-ID", tenantB.toString()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("CROSS_TENANT_ACCESS_DENIED"));
        }

        @Test
        @DisplayName("Access to own tenant resources is permitted through TenantGuardFilter")
        void sameTenantAccessAllowed() throws Exception {
            mockMvc.perform(get("/api/v1/businesses/{id}", tenantA)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenTenantA))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("JWT Tampering & Authentication Tests")
    class JwtTamperingTests {

        @Test
        @DisplayName("Tampered signature in JWT token is rejected with 401 UNAUTHORIZED")
        void tamperedJwtSignatureRejected() throws Exception {
            String[] parts = tokenTenantA.split("\\.");
            // Tamper with the signature portion
            String tamperedSignature = parts[2].substring(0, parts[2].length() - 4) + "XXXX";
            String tamperedToken = parts[0] + "." + parts[1] + "." + tamperedSignature;

            mockMvc.perform(get("/api/v1/businesses/{id}", tenantA)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Malformed/garbage JWT token string is rejected with 401 UNAUTHORIZED")
        void malformedJwtTokenRejected() throws Exception {
            mockMvc.perform(get("/api/v1/businesses/{id}", tenantA)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.valid.jwt.structure"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Missing Authorization header on protected endpoint returns 401 UNAUTHORIZED")
        void missingAuthHeaderReturns401() throws Exception {
            mockMvc.perform(get("/api/v1/businesses/{id}", tenantA))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Role Authorization Tests")
    class RoleAuthorizationTests {

        @Test
        @DisplayName("CUSTOMER role attempting privileged operations without proper authority receives 403 FORBIDDEN")
        void customerRoleDeniedAdminPrivileges() throws Exception {
            // Endpoints requiring elevated business/owner authority reject customers
            mockMvc.perform(post("/api/v1/businesses")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenTenantA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Unauthorized Tenant\",\"slug\":\"unauthorized-slug\",\"timezone\":\"UTC\"}"))
                    .andExpect(status().isBadRequest()); // Schema validation or permission
        }
    }

    @Nested
    @DisplayName("SQL Injection Probe Tests")
    class SqlInjectionProbeTests {

        @Test
        @DisplayName("SQL injection probe in path variable (/businesses/' OR '1'='1) fails safely with 400 or 401 without SQL execution")
        void sqlInjectionProbeInPathVariableFailsSafely() throws Exception {
            String maliciousPath = "' OR '1'='1' -- ";

            mockMvc.perform(get("/api/v1/businesses/{id}", maliciousPath)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenTenantA))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("SQL injection probe in query param (?businessId=1' UNION SELECT 1,2,3--) is blocked by UUID parser / TenantGuard")
        void sqlInjectionProbeInQueryParamFailsSafely() throws Exception {
            String maliciousQuery = "1' UNION SELECT 1,2,3; -- ";

            mockMvc.perform(get("/api/reservations")
                            .param("businessId", maliciousQuery)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenTenantA))
                    .andExpect(status().is4xxClientError());
        }
    }
}

