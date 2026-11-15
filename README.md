# Online Reservation System

A high-concurrency, multi-tenant online reservation platform built with Java 21, Spring Boot 3.3, PostgreSQL, Redis, and Stripe.

## Overview
This system is designed to handle table bookings, hotel rooms, salon appointments, and event ticketing across multiple businesses with extreme burst traffic profiles (e.g. synchronized reservation drop windows).

## Architecture & Decisions
Before writing production business logic, key architectural decisions have been documented and accepted in our Architecture Decision Records:
* [ADR-001: Core Architecture, Modularity, Concurrency, and Transaction Boundaries](docs/adr/ADR-001-core-architecture.md)

### Key Architectural Pillars
1. **Modular Monolith (Spring Modulith):** Domain-driven modules with compile/test-time boundary enforcement.
2. **Package-by-Feature:** Deep encapsulation using package-private Java visibility and clean public facades.
3. **Hybrid Communication:** Direct synchronous facade calls for reads/queries; transactional asynchronous events (Spring Modulith Event Publication Registry) for state transitions and side-effects.
4. **Decoupled Transactions & Stripe Sagas:** Zero external network I/O inside database transactions; 2-phase hold (`PENDING_PAYMENT` -> `CONFIRMED`) with Stripe webhook reconciliation.
5. **Redis Infrastructure:** Distributed locks (Redisson) for hot-slot concurrency, cache-aside for availability queries, and sliding-window rate limiting to absorb spiky burst traffic.
