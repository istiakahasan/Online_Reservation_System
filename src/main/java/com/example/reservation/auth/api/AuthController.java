package com.example.reservation.auth.api;

import com.example.reservation.auth.api.dto.AuthResponseDto;
import com.example.reservation.auth.api.dto.LoginRequestDto;
import com.example.reservation.auth.api.dto.LogoutRequestDto;
import com.example.reservation.auth.api.dto.RefreshTokenRequestDto;
import com.example.reservation.auth.api.dto.RegisterRequestDto;
import com.example.reservation.auth.service.AuthService;
import com.example.reservation.shared.security.AuthenticatedUser;
import com.example.reservation.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/auth", "/api/v1/auth"})
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user authentication, registration, token refresh, and logout")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register customer", description = "Creates a customer user account under a specific tenant")
    @ApiResponse(responseCode = "201", description = "User successfully registered")
    @ApiResponse(responseCode = "409", description = "Email already registered for this tenant")
    public ResponseEntity<AuthResponseDto> register(@Valid @RequestBody RegisterRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticates credentials and returns access token (15m) + refresh token (7d)")
    @ApiResponse(responseCode = "200", description = "Authenticated successfully")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token", description = "Revokes old refresh token and issues a new access token + refresh token pair")
    @ApiResponse(responseCode = "200", description = "Tokens rotated successfully")
    @ApiResponse(responseCode = "401", description = "Refresh token expired or invalid")
    public ResponseEntity<AuthResponseDto> refresh(@Valid @RequestBody RefreshTokenRequestDto request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user", description = "Revokes the supplied refresh token")
    @ApiResponse(responseCode = "204", description = "Logged out successfully")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequestDto request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get current authenticated user profile", description = "Resolves user principal from JWT using @CurrentUser")
    @ApiResponse(responseCode = "200", description = "Current user retrieved")
    @ApiResponse(responseCode = "401", description = "Unauthenticated")
    public ResponseEntity<AuthenticatedUser> getCurrentUser(@CurrentUser AuthenticatedUser currentUser) {
        return ResponseEntity.ok(currentUser);
    }
}
