package com.example.reservation.shared.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry Tracing and Micrometer Observability Configuration.
 * Enables automatic span creation across Controller → Service → Repository layers.
 */
@Configuration
public class ObservabilityConfig {

    /**
     * Enables @Observed annotation processing to create spans across service and repository methods.
     */
    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }

    /**
     * Applies standard global tags to all exported Prometheus/Micrometer metrics.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags("application", "Online_Reservation_System");
    }
}

