# Java Backend Test Plan

This document outlines the test strategy, test categories, and executable test files covering unit, integration, concurrency, security, webhook, and load testing for the Online Reservation System backend.

---

### 1. Unit Tests

- [x] `src/test/java/com/example/reservation/availability/domain/AvailabilityEngineTest.java` — Pure engine tests covering fully booked shifts, partial availability, overlapping time-offs, buffer margins, and European daylight saving time (Europe/Berlin DST spring-forward) slot derivations.
- [x] `src/test/java/com/example/reservation/reservation/domain/ReservationStateMachineTest.java` — Domain state machine test validating legitimate lifecycle transitions (`PENDING → CONFIRMED → CANCELLED / COMPLETED / NO_SHOW`), actor role authorization checks, and rejection of invalid terminal transitions.
- [x] `src/test/java/com/example/reservation/reservation/domain/policy/CancellationPolicyTest.java` — Pure strategy pattern tests verifying deadline enforcement for `WindowBasedCancellationPolicy` along with math and rounding across `FullRefundPolicy`, `PartialRefundPolicy` (50%), and `NoRefundPolicy`.
- [x] `src/test/java/com/example/reservation/reservation/service/CancelReservationUseCaseTest.java` — Orchestration test ensuring `CancelReservationUseCase` delegates to policies, triggers Stripe refund API, emits `ReservationCancelled` domain events, and blocks already cancelled reservations.
- [x] `src/test/java/com/example/reservation/availability/service/AvailabilityServiceImplTest.java` — Service-layer test ensuring working hours, time-offs, and existing bookings are aggregated, passed to the engine, timed via Micrometer, and returned as DTOs.
- [x] `src/test/java/com/example/reservation/reservation/service/ReservationServiceImplTest.java` — Service-layer test validating optimistic locking, slot collision handling, and publishing `ReservationCreated` domain events after transaction commit.
- [x] `src/test/java/com/example/reservation/notification/service/channel/EmailChannelTest.java` — Notification channel test asserting Thymeleaf HTML template processing and `JavaMailSender` dispatch.
- [x] `src/test/java/com/example/reservation/shared/observability/ReservationMetricsTest.java` — Micrometer metrics test verifying counter increments on booking success/conflict and payment failures, latency timer recordings, and active reservations gauge calculations.

---

### 2. Integration Tests (Testcontainers + PostgreSQL + Redis)

- [x] `src/test/java/com/example/reservation/reservation/ReservationConcurrencyIntegrationTest.java` — Testcontainers PostgreSQL integration test verifying database partial unique index enforcement (`idx_reservations_staff_slot_active`), optimistic locking, and HTTP 201 vs 409 conflict handling on booking attempts.
- [x] `src/test/java/com/example/reservation/reservation/jobs/ShedLockConcurrencyIntegrationTest.java` — Testcontainers PostgreSQL and Redis integration test proving ShedLock distributed locks prevent multiple instances from concurrently executing the same background scheduled job.
- [x] `src/test/java/com/example/reservation/reservation/jobs/ReservationScheduledJobsTest.java` — Integration test verifying scheduled cron executions for expiring stale PENDING reservations (older than 15 min), sending 24h reminders, and transitioning to NO_SHOW.
- [x] `src/test/java/com/example/reservation/notification/service/NotificationEventListenerTest.java` — Event-driven integration test verifying `@TransactionalEventListener(phase = AFTER_COMMIT)` triggers notifications independently of reservation transactions, with exponential backoff retries and audit logging in `notification_log`.

---

### 3. Concurrency Tests

- [x] `src/test/java/com/example/reservation/reservation/ReservationConcurrencyIntegrationTest.java` — High-concurrency race test releasing 20 threads simultaneously at the exact same millisecond against a single slot, asserting exactly 1 succeeds (HTTP 201) and 19 fail (HTTP 409) with only 1 database row persisted.

---

### 4. Security Tests

- [x] `src/test/java/com/example/reservation/shared/security/SecurityIntegrationTest.java` — Multi-vector security test suite verifying:
  - **Cross-tenant access**: Blocks requests targeting foreign tenant UUIDs via URI paths or `X-Tenant-ID` headers with 403 Forbidden (`CROSS_TENANT_ACCESS_DENIED`).
  - **JWT tampering**: Verifies that tampered HMAC signatures, expired tokens, or malformed JWT headers return 401 Unauthorized.
  - **Missing role**: Verifies `@PreAuthorize` role enforcement, blocking `CUSTOMER` accounts from accessing owner/administrative operations with 403 Forbidden.
  - **SQL injection probe**: Validates that SQL injection payloads (`' OR '1'='1' --`) in path parameters and query strings are sanitized by Spring Data JPA prepared statements and reject malicious inputs without query leaks or syntax errors.
- [x] `src/test/java/com/example/reservation/shared/observability/MdcLoggingFilterTest.java` — Security and audit logging test asserting request correlation ID, tenant ID, and user ID propagation across MDC context with strict leak cleanup.
- [x] `src/test/java/com/example/reservation/auth/api/AuthControllerTest.java` — Authentication controller security test asserting credential exchange, refresh token rotation, and invalid token rejections.

---

### 5. Webhook Tests

- [x] `src/test/java/com/example/reservation/payment/api/StripeWebhookControllerTest.java` — Webhook controller test asserting valid `Stripe-Signature` HMAC headers return 200 OK, while forged, expired, or tampered signatures reject with 400 Bad Request.
- [x] `src/test/java/com/example/reservation/payment/service/StripeServiceImplTest.java` — Webhook service test asserting idempotency via the `stripe_events` table (replaying the same `event.id` is a no-op that avoids double-confirmations) and handling `payment_intent.payment_failed` rollbacks.

---

### 6. Load Tests (100 Concurrent Bookings)

- [x] `load-tests/gatling/ReservationBookingSimulation.java` — Gatling Java DSL simulation asserting sub-1500ms p95 response time and zero 5xx server errors when 100 concurrent users simultaneously query availability and compete to book a slot.
- [x] `load-tests/jmeter/reservation_100_concurrent_bookings.jmx` — Apache JMeter test plan configuring a 100-thread synchronized burst with dynamic UUIDs verifying availability lookups and HTTP 201/409 assertion boundaries.
