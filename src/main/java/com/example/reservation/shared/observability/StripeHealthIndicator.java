package com.example.reservation.shared.observability;

import com.stripe.Stripe;
import com.stripe.model.Balance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom Actuator Health Indicator verifying Stripe payment gateway reachability.
 * Participates in the /ready readiness probe check alongside Database and Redis.
 */
@Slf4j
@Component("stripe")
public class StripeHealthIndicator implements HealthIndicator {

    @Value("${stripe.api.key:mock_key}")
    private String stripeApiKey;

    @Override
    public Health health() {
        if (stripeApiKey == null || stripeApiKey.isBlank() || stripeApiKey.startsWith("mock")) {
            return Health.up()
                    .withDetail("gateway", "Stripe API")
                    .withDetail("mode", "Mock / Sandbox Mode Active")
                    .withDetail("status", "UP")
                    .build();
        }

        try {
            // Validate connection to Stripe API
            Stripe.apiKey = stripeApiKey;
            // Quick connectivity probe with minimal overhead
            Balance.retrieve();
            return Health.up()
                    .withDetail("gateway", "Stripe API")
                    .withDetail("status", "REACHABLE")
                    .build();
        } catch (Exception ex) {
            log.error("Stripe health check probe failed", ex);
            return Health.down(ex)
                    .withDetail("gateway", "Stripe API")
                    .withDetail("status", "UNREACHABLE")
                    .withDetail("error", ex.getMessage())
                    .build();
        }
    }
}
