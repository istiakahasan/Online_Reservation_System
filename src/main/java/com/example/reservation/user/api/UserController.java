package com.example.reservation.user.api;

import com.example.reservation.user.api.dto.UpdateUserRequestDto;
import com.example.reservation.user.api.dto.UserResponseDto;
import com.example.reservation.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Endpoints for managing user accounts and profiles")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    @Operation(summary = "Get user profile", description = "Retrieves user details by user ID")
    @ApiResponse(responseCode = "200", description = "User profile found")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @GetMapping
    @Operation(summary = "List users for tenant", description = "Retrieves all users belonging to a tenant business")
    @ApiResponse(responseCode = "200", description = "Users listed successfully")
    public ResponseEntity<List<UserResponseDto>> getUsersByBusiness(@RequestParam UUID businessId) {
        return ResponseEntity.ok(userService.getUsersByBusiness(businessId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user profile", description = "Updates profile attributes for an existing user")
    @ApiResponse(responseCode = "200", description = "User updated successfully")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<UserResponseDto> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequestDto request
    ) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }
}
