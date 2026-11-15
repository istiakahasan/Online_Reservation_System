package com.example.reservation.shared.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthCheckControllerTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private RedisConnectionFactory redisConnectionFactory;

    @Mock
    private StripeHealthIndicator stripeHealthIndicator;

    @Mock
    private Connection dbConnection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private RedisConnection redisConnection;

    @InjectMocks
    private HealthCheckController healthCheckController;

    @Test
    @DisplayName("Should return 200 OK UP for liveness probe")
    void shouldReturn200ForLiveness() {
        ResponseEntity<Map<String, Object>> response = healthCheckController.liveness();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("UP");
        assertThat(response.getBody().get("probe")).isEqualTo("liveness");
    }

    @Test
    @DisplayName("Should return 200 OK UP when DB, Redis, and Stripe are ready")
    void shouldReturn200WhenAllSubsystemsReady() throws Exception {
        // Mock DB healthy
        when(dataSource.getConnection()).thenReturn(dbConnection);
        when(dbConnection.prepareStatement(anyString())).thenReturn(preparedStatement);

        // Mock Redis healthy
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");

        // Mock Stripe healthy
        when(stripeHealthIndicator.health()).thenReturn(Health.up().withDetail("status", "Reachable").build());

        ResponseEntity<Map<String, Object>> response = healthCheckController.readiness();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("UP");

        Map<String, Object> components = (Map<String, Object>) response.getBody().get("components");
        assertThat(components).containsKeys("database", "redis", "stripe");
    }

    @Test
    @DisplayName("Should return 503 SERVICE_UNAVAILABLE when DB fails")
    void shouldReturn503WhenDatabaseFails() throws Exception {
        // Mock DB throws exception
        when(dataSource.getConnection()).thenThrow(new RuntimeException("Connection refused"));

        // Mock Redis & Stripe healthy
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");
        when(stripeHealthIndicator.health()).thenReturn(Health.up().build());

        ResponseEntity<Map<String, Object>> response = healthCheckController.readiness();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("DOWN");
    }
}
