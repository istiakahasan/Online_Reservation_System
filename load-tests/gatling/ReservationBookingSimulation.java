package com.example.reservation.load;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;
import java.util.UUID;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Gatling load testing simulation testing 100 concurrent users competing
 * for availability checks and reservation creation under heavy concurrency.
 */
public class ReservationBookingSimulation extends Simulation {

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(System.getProperty("baseUrl", "http://localhost:8080"))
            .acceptHeader("application/json")
            .contentTypeHeader("application/json")
            .shareConnections();

    private final ScenarioBuilder scn = scenario("100 Concurrent Slot Bookings")
            .exec(session -> session
                    .set("businessId", "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
                    .set("serviceId", "11111111-1111-1111-1111-111111111111")
                    .set("staffId", "22222222-2222-2222-2222-222222222222")
                    .set("customerId", UUID.randomUUID().toString())
            )
            // Step 1: Query slot availability
            .exec(http("Check Availability")
                    .get("/api/v1/businesses/#{businessId}/availability")
                    .queryParam("serviceId", "#{serviceId}")
                    .queryParam("staffId", "#{staffId}")
                    .queryParam("date", "2026-11-20")
                    .check(status().is(200))
            )
            .pause(Duration.ofMillis(200))
            // Step 2: Attempt concurrent reservation creation
            .exec(http("Attempt Slot Booking")
                    .post("/api/reservations")
                    .header("X-Tenant-ID", "#{businessId}")
                    .body(StringBody("""
                        {
                            "businessId": "#{businessId}",
                            "customerId": "#{customerId}",
                            "serviceId": "#{serviceId}",
                            "staffId": "#{staffId}",
                            "startTime": "2026-11-20T14:00:00Z",
                            "notes": "Gatling concurrent load test"
                        }
                    """)).asJson()
                    // Exactly 1 winner gets 201 Created; 99 get 409 Conflict. Neither should throw 500
                    .check(status().in(201, 409))
            );

    {
        setUp(
                scn.injectOpen(
                        atOnceUsers(100) // 100 concurrent threads fired simultaneously
                )
        ).protocols(httpProtocol)
         .assertions(
                global().failedRequests().percent().lte(0.0), // Zero unhandled 5xx errors
                global().responseTime().percentile(95.0).lte(1500), // p95 latency under 1500ms
                global().responseTime().percentile(99.0).lte(2500)  // p99 latency under 2500ms
         );
    }
}
