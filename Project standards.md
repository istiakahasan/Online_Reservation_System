# Project Standards — Agent Guidelines & Architecture Rules

This document defines the strict engineering guidelines, architectural rules, and coding standards that all developers and AI assistants must strictly follow in every session when contributing to the **Online Reservation System**.

---

## 1. Tech Stack & Runtime Baseline

* **Java Version:** Java 21 (LTS)
  * Use modern Java features: pattern matching, switch expressions, `record`s, text blocks, and sequenced collections.
  * Virtual Threads enabled: `spring.threads.virtual.enabled=true`.
* **Framework:** Spring Boot 3.3.x
* **Persistence & Storage:** PostgreSQL 16+ via Spring Data JPA, Flyway migrations.
* **Cache & Distributed State:** Redis 7+ via Redisson / Spring Data Redis.
* **External Services:** Stripe SDK for payment processing.

---

## 2. Package-by-Feature Structure

The codebase is organized strictly **by feature**, not by layer. All business modules reside under `com.example.reservation.<feature>`.

### Canonical Package Layout
```text
com.example.reservation.<feature>/
├── api/
│   ├── <Feature>Controller.java        # REST endpoints (@RestController)
│   ├── <Feature>RequestDto.java        # Inbound request records
│   └── <Feature>ResponseDto.java       # Outbound response records
├── service/
│   ├── <Feature>Service.java           # Service interface or direct service class
│   └── <Feature>ServiceImpl.java       # Implementation (if interface exists)
├── domain/
│   ├── <Feature>Entity.java            # JPA entity class
│   ├── <Feature>Status.java            # Domain enums & value objects
│   └── exception/                      # Feature-specific domain exceptions
│       └── <Feature>NotFoundException.java
├── repo/
│   └── <Feature>Repository.java        # Spring Data JPA Repository
└── mapper/
    └── <Feature>Mapper.java            # MapStruct or explicit mapper
```

* Features must remain cohesive. Inter-feature communication must happen via public service/facade interfaces or Spring domain events. Never cross-import internal implementation details from other feature packages.

---

## 3. Class Naming & Role Conventions

Every class name must explicitly describe its architectural role:

| Component Type | Suffix / Pattern | Example |
| :--- | :--- | :--- |
| **REST Controller** | `*Controller` | `BookingController` |
| **Business Service** | `*Service` | `BookingService` |
| **Data Repository** | `*Repository` | `BookingRepository` |
| **JPA Entity** | `*Entity` | `BookingEntity`, `SlotEntity` |
| **Data Transfer Object** | `*Dto`, `*RequestDto`, `*ResponseDto` | `CreateBookingRequestDto` |
| **Data Mapper** | `*Mapper` | `BookingMapper` |
| **Domain Exception** | `*Exception` | `SlotAlreadyReservedException` |

---

## 4. DTOs and JPA Entities Rules

### DTOs
* **Always use Java `record`s** for DTOs.
* DTOs are immutable data carriers across the API boundary.
* Do not expose JPA entities through REST controllers. Always map to/from DTOs.

### JPA Entities
* Entities are standard mutable Java classes annotated with `@Entity` and `@Table`.
* **Lombok Rules for Entities:**
  * Use `@Getter`, `@Setter`.
  * Use `@NoArgsConstructor(access = AccessLevel.PROTECTED)`.
  * **NEVER** use `@Data`, `@ToString`, or `@EqualsAndHashCode` on JPA entities (avoids infinite loops in bidirectional relationships and broken hash codes on unpersisted entities).
  * Explicitly implement `equals()` and `hashCode()` based on business key or database ID if needed.

---

## 5. Input Validation Rules

* Apply `jakarta.validation.constraints.*` (`@NotNull`, `@NotBlank`, `@Positive`, `@Size`, etc.) **strictly on DTOs**.
* Annotate `@RequestBody @Valid` on controller methods.
* **NEVER place validation annotations on JPA entities.** Database constraints (e.g., `nullable = false`, unique indexes, check constraints) are managed via Flyway SQL scripts, while application-level validation belongs in DTOs and domain service invariants.

---

## 6. Dependency Injection

* **NEVER use field injection** (i.e. `@Autowired private FooService fooService;` is strictly forbidden).
* **Constructor injection only.**
* Prefer Lombok's `@RequiredArgsConstructor` over `private final` fields, or declare explicit constructor injection.

```java
// CORRECT
@Service
@RequiredArgsConstructor
public class BookingService {
    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher eventPublisher;
}

// FORBIDDEN
@Service
public class BookingService {
    @Autowired
    private BookingRepository bookingRepository; // Violation!
}
```

---

## 7. Exception Handling & Error Representation

* All domain and business exceptions must extend a common abstract base class:
  ```java
  public abstract class AppException extends RuntimeException {
      private final String errorCode;
      private final HttpStatus status;
      // constructor & getters
  }
  ```
* All feature exceptions reside in `com.example.reservation.<feature>.domain.exception`.
* Use a centralized `@RestControllerAdvice` in `com.example.reservation.common.exception.GlobalExceptionHandler`:
  * Map `AppException` and its subtypes to standardized HTTP responses (RFC 7807 `ProblemDetail` or standard error envelope).
  * Map `MethodArgumentNotValidException` to structured field-level validation errors.
  * Catch unhandled `Exception` and return generic 500 status without leaking internal stack traces.

---

## 8. Database Schema & Flyway Migrations

* **Flyway is the sole owner of database schema changes.**
* Migration scripts must be placed in `src/main/resources/db/migration/` using strict version prefixes:
  * Example: `V1__init_schema.sql`, `V2__add_tenant_id_to_slots.sql`.
* **NEVER set `spring.jpa.hibernate.ddl-auto=update` or `create-drop`** in application configuration.
  * Always use `spring.jpa.hibernate.ddl-auto=validate` (or `none`).

---

## 9. Timezones & Temporal Data

* **Storage:** Always store timestamps in **UTC** using `java.time.Instant` or `java.time.OffsetDateTime` (with UTC offset).
* **Display / API:** Convert to the respective business or tenant timezone at the presentation boundary using the tenant's configured `ZoneId`.
* **Never** use legacy `java.util.Date`, `java.sql.Timestamp`, or `java.util.Calendar`.

---

## 10. Logging & Distributed Tracing (MDC)

* Use SLF4J (via Lombok `@Slf4j` or `LoggerFactory.getLogger(...)`).
* Use parameterized logging: `log.info("Booking created: id={}, tenant={}", booking.getId(), tenantId)`.
* Every inbound request must pass through a Filter/Interceptor that populates SLF4J's `MDC` with:
  * `requestId`: Unique correlation UUID for the request.
  * `tenantId`: Extracted tenant identifier.
* Clean up `MDC.clear()` in a `finally` block before request thread termination.
* Never log sensitive information (credit card tokens, Stripe secrets, passwords, or PII).

---

## 11. Testing Standards

* **Unit Tests (Services):**
  * Every service must have a dedicated unit test.
  * Use JUnit 5 and Mockito (`@ExtendWith(MockitoExtension.class)`).
  * Test business logic, boundary conditions, and exception scenarios in isolation without launching the Spring context.
* **Web Layer Tests (Controllers):**
  * Every controller must have a `@WebMvcTest` suite.
  * Use `MockMvc` to verify HTTP status codes, request serialization/deserialization, validation errors, and response payloads.
  * Mock service dependencies using `@MockBean`.
* **Integration Tests:**
  * Must use **Testcontainers** for real PostgreSQL and Redis instances.
  * **Never** use H2 in-memory database for integration tests (ensures real PostgreSQL dialect, JSONB, and unique constraint behavior are verified).

---

## 12. Transaction Boundaries & Payment Integration

* Keep database transactions (`@Transactional`) as short as possible.
* **NEVER perform external network calls (Stripe API, HTTP clients, emails) inside a `@Transactional` block.**
* Follow the 2-phase hold pattern:
  1. Acquire short DB transaction to create a temporary hold (`PENDING_PAYMENT`).
  2. Call Stripe API outside the database transaction.
  3. Confirm the reservation via Stripe webhook in a separate, isolated transaction.
