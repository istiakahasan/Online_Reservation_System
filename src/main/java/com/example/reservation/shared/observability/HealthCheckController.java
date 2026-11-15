package com.example.reservation.shared.observability;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controller exposing lightweight /health (liveness) and /ready (readiness)
 * endpoints verifying PostgreSQL, Redis, and Stripe reachability.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Health & Readiness", description = "Probes for application liveness and subsystem readiness")
public class HealthCheckController {

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final StripeHealthIndicator stripeHealthIndicator;

    @GetMapping({"/health", "/actuator/health/liveness"})
    @Operation(summary = "Liveness probe", description = "Returns UP if the application process is running and accepting traffic")
    @ApiResponse(responseCode = "200", description = "Application is alive")
    public ResponseEntity<Map<String, Object>> liveness() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "probe", "liveness"
        ));
    }

    @GetMapping({"/ready", "/actuator/health/readiness"})
    @Operation(summary = "Readiness probe", description = "Verifies reachability of Database, Redis, and Stripe before routing traffic")
    @ApiResponse(responseCode = "200", description = "All subsystems are ready")
    @ApiResponse(responseCode = "503", description = "One or more subsystems (DB/Redis/Stripe) are unavailable")
    public ResponseEntity<Map<String, Object>> readiness() {
        Map<String, Object> components = new LinkedHashMap<>();
        boolean isAllReady = true;

        // 1. Check PostgreSQL Database
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT 1")) {
            stmt.execute();
            components.put("database", Map.of("status", "UP", "details", "PostgreSQL connection validated"));
        } catch (Exception e) {
            log.error("Readiness check: Database probe failed", e);
            components.put("database", Map.of("status", "DOWN", "error", e.getMessage() != null ? e.getMessage() : "Connection failed"));
            isAllReady = false;
        }

        // 2. Check Redis
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String pingResult = connection.ping();
            components.put("redis", Map.of("status", "UP", "ping", pingResult != null ? pingResult : "PONG"));
        } catch (Exception e) {
            log.error("Readiness check: Redis probe failed", e);
            components.put("redis", Map.of("status", "DOWN", "error", e.getMessage() != null ? e.getMessage() : "Redis unreachable"));
            isAllReady = false;
        }

        // 3. Check Stripe
        try {
            Health stripeHealth = stripeHealthIndicator.health();
            components.put("stripe", Map.of(
                    "status", stripeHealth.getStatus().getCode(),
                    "details", stripeHealth.getDetails()
            ));
            if (!Status.UP.equals(stripeHealth.getStatus())) {
                isAllReady = false;
            }
        } catch (Exception e) {
            log.error("Readiness check: Stripe probe failed", e);
            components.put("stripe", Map.of("status", "DOWN", "error", e.getMessage()));
            isAllReady = false;
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", isAllReady ? "UP" : "DOWN");
        response.put("probe", "readiness");
        response.put("components", components);

        return ResponseEntity.status(isAllReady ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}
