package com.example.reservation.auth.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.reservation.auth.api.dto.AuthResponseDto;
import com.example.reservation.auth.api.dto.LoginRequestDto;
import com.example.reservation.auth.api.dto.LogoutRequestDto;
import com.example.reservation.auth.api.dto.RefreshTokenRequestDto;
import com.example.reservation.auth.api.dto.RegisterRequestDto;
import com.example.reservation.auth.service.AuthService;
import com.example.reservation.auth.service.JwtService;
import com.example.reservation.shared.config.WebMvcConfig;
import com.example.reservation.shared.exception.AppException;
import com.example.reservation.shared.security.CurrentUserArgumentResolver;
import com.example.reservation.shared.security.JwtAuthenticationFilter;
import com.example.reservation.shared.security.SecurityConfig;
import com.example.reservation.shared.security.TenantGuardFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, TenantGuardFilter.class, WebMvcConfig.class, CurrentUserArgumentResolver.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    private final UUID businessId = UUID.randomUUID();
    private final UUID otherBusinessId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Nested
    @DisplayName("1. Happy Path Scenarios")
    class HappyPathTests {

        @Test
        @DisplayName("POST /api/auth/register creates user and returns token pair")
        void shouldRegisterSuccessfully() throws Exception {
            RegisterRequestDto request = new RegisterRequestDto(
                    businessId, "Alice", "Smith", "alice@example.com", "+1234567890", "P@ssw0rd123!"
            );
            AuthResponseDto response = AuthResponseDto.of(
                    "mock-access-token", "mock-refresh-token", 900L, userId, businessId, "CUSTOMER"
            );

            when(authService.register(any(RegisterRequestDto.class))).thenReturn(response);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accessToken").value("mock-access-token"))
                    .andExpect(jsonPath("$.refreshToken").value("mock-refresh-token"))
                    .andExpect(jsonPath("$.role").value("CUSTOMER"));
        }

        @Test
        @DisplayName("POST /api/auth/login returns accessToken (15m) and refreshToken (7d)")
        void shouldLoginSuccessfully() throws Exception {
            LoginRequestDto request = new LoginRequestDto(businessId, "alice@example.com", "P@ssw0rd123!");
            AuthResponseDto response = AuthResponseDto.of(
                    "valid-access-jwt", "valid-refresh-token", 900L, userId, businessId, "CUSTOMER"
            );

            when(authService.login(any(LoginRequestDto.class))).thenReturn(response);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("valid-access-jwt"))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(900));
        }

        @Test
        @DisplayName("POST /api/auth/refresh rotates refresh token")
        void shouldRotateRefreshTokenSuccessfully() throws Exception {
            RefreshTokenRequestDto request = new RefreshTokenRequestDto("old-refresh-token");
            AuthResponseDto response = AuthResponseDto.of(
                    "new-access-jwt", "new-rotated-refresh-token", 900L, userId, businessId, "CUSTOMER"
            );

            when(authService.refresh(eq("old-refresh-token"))).thenReturn(response);

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("new-access-jwt"))
                    .andExpect(jsonPath("$.refreshToken").value("new-rotated-refresh-token"));
        }

        @Test
        @DisplayName("POST /api/auth/logout revokes refresh token with 204 No Content")
        void shouldLogoutSuccessfully() throws Exception {
            LogoutRequestDto request = new LogoutRequestDto("active-refresh-token");
            doNothing().when(authService).logout(eq("active-refresh-token"));

            mockMvc.perform(post("/api/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("2. Authentication Failure Scenarios")
    class FailureTests {

        @Test
        @DisplayName("POST /api/auth/login with invalid credentials returns 401 Unauthorized")
        void shouldReturn401OnInvalidCredentials() throws Exception {
            LoginRequestDto request = new LoginRequestDto(businessId, "wrong@example.com", "WrongPassword!");

            when(authService.login(any(LoginRequestDto.class)))
                    .thenThrow(new AppException("Invalid email or password", "BAD_CREDENTIALS", HttpStatus.UNAUTHORIZED) {});

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/auth/me with expired token returns 401 Unauthorized")
        void shouldReturn401OnExpiredToken() throws Exception {
            when(jwtService.parseAndValidateClaims("expired-jwt"))
                    .thenThrow(new io.jsonwebtoken.ExpiredJwtException(null, null, "JWT token expired"));

            mockMvc.perform(get("/api/auth/me")
                            .header("Authorization", "Bearer expired-jwt"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Cross-tenant access returns 403 Forbidden via TenantGuardFilter")
        void shouldReturn403OnCrossTenantAccess() throws Exception {
            // Mock valid JWT issued for tenant 'businessId'
            io.jsonwebtoken.Claims mockClaims = io.jsonwebtoken.Jwts.claims()
                    .subject(userId.toString())
                    .add("tenantId", businessId.toString())
                    .add("email", "alice@example.com")
                    .add("role", "CUSTOMER")
                    .build();

            when(jwtService.parseAndValidateClaims("valid-tenant-token")).thenReturn(mockClaims);

            // Accessing with X-Tenant-ID header belonging to 'otherBusinessId'
            mockMvc.perform(get("/api/auth/me")
                            .header("Authorization", "Bearer valid-tenant-token")
                            .header("X-Tenant-ID", otherBusinessId.toString()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("CROSS_TENANT_ACCESS_DENIED"));
        }
    }
}
