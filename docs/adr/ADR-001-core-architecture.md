# Architecture Decision Record (ADR)

**Document ID:** ADR-001  
**Title:** Core Architecture, Modularity, Concurrency, and Transaction Boundaries for Online Reservation System  
**Status:** Accepted  
**Date:** 2026-11-15  
**Deciders:** Lead Architect / Small Engineering Team  

---

## 1. Context & Problem Statement

We are designing a multi-tenant, high-concurrency online reservation system handling table/slot bookings across hundreds of businesses.

### Architectural Baseline
* **Domain:** Multi-tenant reservation platform (restaurant table bookings, salon appointments, hotel rooms, and time-slot reservations).
* **Scale & Load Profile:** 
  * Baseline: ~50,000 bookings/day across ~1,000 active businesses.
  * Traffic: Highly spiky. High-demand venues release slots at specific times (e.g., daily at 09:00 or midnight), creating extreme burst concurrency on specific inventory partitions ("hot slots").
* **Team Profile:** Solo to small engineering team (1–3 engineers). High operational simplicity, rapid iteration, and developer ergonomics are paramount.
* **Technology Stack:**
  * **Runtime:** Java 21 (LTS) leveraging Virtual Threads for high I/O throughput.
  * **Framework:** Spring Boot 3.3.
  * **Database:** PostgreSQL 16+ (ACID single source of truth).
  * **In-Memory Store:** Redis 7+ (distributed locks, hot caches, rate limiting).
  * **External Gateway:** Stripe (payment intents and webhooks).

---

## 2. Decision Summary Table

| Area | Decision | Primary Driver |
| :--- | :--- | :--- |
| **1. Architecture Pattern** | **Modular Monolith (Spring Modulith)** | Eliminates distributed systems operational overhead while enforcing clean architectural boundaries. |
| **2. Packaging Strategy** | **Package-by-Feature / Component** | High cohesion, encapsulation via Java package-private visibility, and direct mapping to business capabilities. |
| **3. Inter-Module Communication** | **Hybrid:** Direct Interface Calls for Reads; **Spring Modulith Transactional Events** for Side Effects | Avoids complex distributed sagas while maintaining strong consistency for inventory validation. |
| **4. Transaction Boundaries** | **Decoupled 2-Phase Hold:** Short DB transactions; **Zero external network I/O inside DB transactions** | Prevents DB connection pool starvation during Stripe payment latency and handles concurrency spikes cleanly. |
| **5. Redis Responsibilities** | **Distributed Locking (Redisson), Multi-Tier Caching, Sliding-Window Rate Limiting** | Shields PostgreSQL from lock contention during slot drops, throttles abusive clients, and serves high-read inventory. |

---

## 3. Detailed Decisions & Justifications

### Decision 1: Modular Monolith (Spring Modulith) vs. Microservices

#### Decision
We adopt a **Modular Monolith** architecture built on **Spring Modulith 1.2+ (on Spring Boot 3.3)** deployed as a single runtime artifact.

#### Justification
1. **Team Size & Operational Tax:** A small team cannot justify the operational toll of microservices (distributed tracing, complex CI/CD matrices, Kubernetes cluster orchestration, cross-service schema migrations, and network partitions).
2. **Compile-Time and Test-Time Architectural Verification:** Spring Modulith allows us to define `@ApplicationModule` boundaries and verify them using built-in ArchUnit tests (`ApplicationModules.of(...).verify()`). This prevents domain bleed and "spaghetti" dependencies without the friction of network hops.
3. **Evolutionary Architecture:** By maintaining strict module boundaries with independent domain models and schemas, any module (e.g., `inventory` or `payment`) can be carved out into an independent microservice in the future if physical scaling limits are reached.

#### Alternatives Rejected
* **Microservices:** Premature distribution introduces distributed transaction challenges (Two-Phase Commit / Sagas), higher latency, infrastructure costs, and deployment complexity that would slow a small team down.
* **Traditional Layered Monolith:** Quickly devolves into a "Big Ball of Mud" with circular dependencies and tight coupling across service layers.

---

### Decision 2: Package-by-Feature vs. Package-by-Layer

#### Decision
We adopt **Package-by-Feature (Package-by-Component/Module)** aligned with Domain-Driven Design (DDD) bounded contexts.

#### Structure
```text
com.reservation.system
├── availability/               <-- Modulith Boundary: Slot computation & calendars
│   ├── internal/               <-- Package-private implementation details
│   │   ├── AvailabilityEngine.java
│   │   ├── SlotRepository.java
│   │   └── SlotEntity.java
│   ├── AvailabilityService.java <-- Public exported facade / API
│   └── SlotView.java           <-- Immutable DTO
├── booking/                    <-- Modulith Boundary: Reservation lifecycle
│   ├── internal/
│   │   ├── BookingEngine.java
│   │   ├── BookingRepository.java
│   │   └── BookingEntity.java
│   ├── BookingFacade.java      <-- Public interface
│   ├── BookingCreatedEvent.java
│   └── BookingStatus.java
├── payment/                    <-- Modulith Boundary: Stripe integration
├── notification/               <-- Modulith Boundary: Email/SMS confirmations
└── tenant/                     <-- Modulith Boundary: Business profiles & settings
```

#### Justification
1. **Encapsulation via Java Visibility:** In a package-by-layer design (`controllers`, `services`, `repositories`), classes must be `public` to be accessible across layers, exposing domain entities and internal repositories to accidental cross-cutting access. Package-by-feature allows internal entities, repositories, and helper services to remain **package-private**, exposing only a minimal, public API or Facade.
2. **Cognitive Load & Cohesion:** A change to the booking flow only touches the `com.reservation.system.booking` package, rather than navigating four disparate layer packages.

#### Alternatives Rejected
* **Package-by-Layer:** Low cohesion, high coupling. Obscures business domain concepts and fails to enforce boundaries between distinct functional units.

---

### Decision 3: Inter-Module Communication (Spring Events vs. Direct Calls)

#### Decision
We adopt a **Hybrid Model**:
1. **Synchronous Direct Calls (Facade interfaces):** Strictly for read-only queries, pre-condition checks, and data aggregation where immediate consistency is required.
2. **Asynchronous Spring Modulith Events (Transactional Outbox):** For all cross-module state transitions, workflows, and asynchronous side-effects.

```text
+---------------+                              +---------------+
|    Booking    | --(1. Sync Read Facade)----> | Availability  |
|    Module     |                              +---------------+
|               |
|               | --(2. Publish Domain Event)-> [Spring Modulith Event Registry]
+---------------+                                        |
                                             (Asynchronous / Outbox)
                                                         |
                                 +-----------------------+-----------------------+
                                 |                                               |
                                 v                                               v
                         +---------------+                               +---------------+
                         |    Payment    |                               | Notification  |
                         |    Module     |                               |    Module     |
                         +---------------+                               +---------------+
```

#### Justification
1. **Eliminating Cascading Failures:** Emitting events for side-effects (e.g., dispatching booking confirmation emails, updating audit logs) ensures that failures in auxiliary systems do not abort the core booking transaction.
2. **Spring Modulith Event Publication Registry:** Spring Modulith automatically persists application events to an `event_publication` table in PostgreSQL *within the active transaction*. If the application crashes before an event is processed by listeners, it is retried upon reboot. This provides the reliability of a Transactional Outbox pattern without deploying Kafka or RabbitMQ.
3. **When Direct Calls Are Permitted:** When the `booking` module must compute whether a table/room is structurally valid prior to attempting a lock, it invokes `AvailabilityFacade.checkSlotConfiguration(...)` synchronously. Read dependencies across modules are permitted; write mutations across module boundaries must be event-driven.

#### Alternatives Rejected
* **Pure Direct Calls (Synchronous):** Causes tightly coupled write transactions across boundaries. A failure in the notification module could roll back a successful reservation.
* **External Message Broker (Kafka/RabbitMQ):** Introduces operational overhead and cluster management not warranted at this stage. Spring Modulith's DB-backed event registry satisfies at-least-once delivery semantics within the monolith.

---

### Decision 4: Transaction Boundaries & External Payment Integration

#### Decision
1. **Zero External Network Calls Inside Database Transactions:** Database transactions (`@Transactional`) must never wrap external network calls (specifically Stripe API requests).
2. **Two-Phase Hold Model:** Reservations use a two-phase state machine (`PENDING_PAYMENT` -> `CONFIRMED` / `EXPIRED`).

```text
[Client]                [Booking Module]          [PostgreSQL]              [Stripe]
   |                           |                        |                      |
   |-- 1. POST /reservations ->|                        |                      |
   |                           |-- 2. BEGIN TX -------->|                      |
   |                           |   Create PENDING hold  |                      |
   |                           |   (expires in 10 mins) |                      |
   |                           |-- 3. COMMIT TX ------->|                      |
   |                           |                                               |
   |                           |-- 4. Create PaymentIntent (Outside TX) ------>|
   |                           |<- 5. Return Client Secret --------------------|
   |<- 6. Return Checkout UI --|                                               |
   |                                                                           |
   |-- 7. Complete Payment via Stripe SDK ------------------------------------>|
                                                                               |
                               [Webhook Handler]                               |
                                       |<- 8. Webhook: payment_intent.succeeded|
                                       |-- 9. BEGIN TX ------->|               |
                                       |   Transition to       |               |
                                       |   CONFIRMED           |               |
                                       |-- 10. COMMIT TX ----->|               |
```

#### Justification
1. **Connection Pool Depletion:** Stripe API calls typically take 200ms–1500ms. If an external API call is executed inside a `@Transactional` boundary, the underlying PostgreSQL connection is pinned for the entire duration. Under spiky load (e.g., 500 concurrent booking attempts), the database connection pool (e.g., HikariCP with 20–50 connections) is exhausted immediately, degrading system-wide availability.
2. **State Machine Integrity:**
   * **Phase 1 (Hold Creation):** A short-lived DB transaction creates the reservation with status `PENDING_PAYMENT` and a `hold_expires_at` timestamp (e.g., 10 minutes).
   * **Phase 2 (Payment Intent Generation):** Outside the DB transaction, the system calls Stripe to create a `PaymentIntent` and attaches the internal `reservation_id` in Stripe metadata.
   * **Phase 3 (Confirmation via Webhook):** The single source of truth for payment success is the **Stripe Webhook** (`payment_intent.succeeded`). The webhook handler initiates a short, dedicated DB transaction that verifies the hold has not expired, marks the booking `CONFIRMED`, and publishes a `BookingConfirmedEvent`.
   * **Phase 4 (Expiry Sweeper):** A background scheduled job sweeps orphaned `PENDING_PAYMENT` records where `hold_expires_at < NOW()` and releases inventory.

#### Alternatives Rejected
* **Synchronous Payment in Single Transaction:** High risk of connection pool exhaustion, deadlocks, and partial failure states (e.g., money deducted on Stripe, but DB transaction rolls back due to a constraint violation).

---

### Decision 5: Redis Topology & Responsibilities

#### Decision
Redis is used across three specific infrastructure concerns: **Distributed Locking**, **Hot-Path Read Caching**, and **Sliding-Window Rate Limiting**.

```text
                           Incoming Request
                                  |
                                  v
                   +------------------------------+
                   |  1. Redis Rate Limiter       |  (Drop Protection: Sliding Window)
                   +------------------------------+
                                  |
                                  v
                   +------------------------------+
                   |  2. Redis Cache              |  (Fast Read: Slot Availability)
                   +------------------------------+
                                  |
                        (Cache Miss / Booking)
                                  |
                                  v
                   +------------------------------+
                   |  3. Redisson Dist. Lock      |  (Concurrency: Slot Mutex)
                   +------------------------------+
                                  |
                                  v
                   +------------------------------+
                   |  PostgreSQL Database         |  (System of Record)
                   +------------------------------+
```

#### Justification & Implementation Plan

##### 1. Distributed Locking (Concurrency & Double-Booking Prevention)
* **Problem:** During high-demand reservation drops, hundreds of requests target the exact same table/slot within milliseconds.
* **Mechanism:** We use **Redisson** to implement a distributed lock keyed by `lock:slot:{businessId}:{slotId}`.
* **Flow:**
  * Acquire lock with a fast lease timeout (e.g., 3 seconds) and wait time (e.g., 500ms).
  * Check slot availability in DB.
  * Write `PENDING_PAYMENT` hold.
  * Release lock.
* **PostgreSQL Backup:** A database-level unique constraint (`UNIQUE(business_id, resource_id, start_time, status_active)`) serves as the ultimate safety net against race conditions, ensuring that even if a lock expires prematurely, the DB rejects overlapping confirmed/held bookings.

##### 2. Hot-Path Caching (Availability & Calendars)
* **Problem:** Availability queries outnumber booking mutations by orders of magnitude (typically 50:1).
* **Strategy:** Cache-Aside with short TTL (e.g., 30–60 seconds) for computed slot calendars (`cache:availability:{businessId}:{date}`).
* **Invalidation:** When a reservation state changes (`BookingCreatedEvent`, `BookingCancelledEvent`), an event listener proactively invalidates or updates the specific day's cache key.

##### 3. Sliding-Window Rate Limiting
* **Problem:** Slot drop events attract aggressive refreshes and automated scraping bots.
* **Mechanism:** Redis-backed sliding window rate limiter (executed via atomic Lua script or Redis Cell) applied at:
  * IP level (e.g., max 60 requests/minute on public booking APIs).
  * Tenant/API Key level for partner integrations.
* **Outcome:** Rejects abusive burst traffic at the gateway layer before it can saturate application threads or database connections.

#### Alternatives Rejected
* **PostgreSQL-Only Row Locking (`SELECT ... FOR UPDATE`):** While strictly correct, issuing `FOR UPDATE` under intense burst traffic causes connection pool contention and database CPU spikes on lock queues. Redis absorbs the lock contention before requests reach the database engine.
* **In-Memory JVM Locks (`ReentrantLock`):** Fails as soon as the application scales horizontally to two or more application instances.

---

## 4. Architectural Consequences & Trade-Offs

### Positive Consequences
* **Operational Simplicity:** A single deployable service container reduces infrastructure costs, monitoring overhead, and CI/CD maintenance.
* **Resilience Under Spikes:** Redis handles rate limiting and locks off-process; PostgreSQL handles durable, short-lived transactions; Virtual Threads (Java 21) handle high-concurrency waiting on Redis and Stripe I/O.
* **Safe External Interactions:** Decoupling Stripe from DB transactions guarantees the database pool remains healthy regardless of third-party API latency.
* **Modularity Preserved:** Spring Modulith enforces clean boundaries, providing a straightforward migration path to microservices if specific modules require independent scaling in the future.

### Negative Consequences & Mitigations
* **Eventual Consistency in Notification & Analytics:**
  * *Risk:* User confirmations might experience small delays (hundreds of milliseconds).
  * *Mitigation:* The UI provides an immediate confirmation screen based on the synchronous response; email/SMS dispatch follows asynchronously.
* **Redis Operational Dependency:**
  * *Risk:* If Redis becomes unavailable, distributed locking and rate limiting fail.
  * *Mitigation:* Redis is configured in a high-availability primary/replica setup with automatic failover (AWS ElastiCache or Redis Sentinel). If Redis fails, the system falls back to PostgreSQL pessimistic row locks with degraded throughput.
* **Orphaned Reservation Holds:**
  * *Risk:* Users start checkout on Stripe and abandon the browser tab, leaving slots in `PENDING_PAYMENT`.
  * *Mitigation:* A lightweight background sweeper runs every 60 seconds to expire stale holds and release inventory back into the available pool.

---

## 5. Review & Approval

* **Approved By:** Architecture Review Team
* **Next Steps:**
  1. Initialize Spring Boot 3.3 project with Java 21 toolchain and Spring Modulith starter dependencies.
  2. Define module root packages and verify module boundaries with ArchUnit/Spring Modulith tests.
  3. Configure Redisson client and PostgreSQL schema with strict unique constraints.
