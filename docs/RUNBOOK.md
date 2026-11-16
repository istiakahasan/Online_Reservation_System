# Deployment & Incident Runbook

Operational guide for the Online Reservation System (Spring Boot 3 / Java 21, PostgreSQL 16, Redis 7, Stripe, SMTP).
All SQL below was written against the actual schema in `src/main/resources/db/migration` (V1–V5).

> **Conventions:** `$DB` = `psql "$DATABASE_URL"`-style connection. Run diagnosis queries on a **read replica or in a read-only transaction** where possible. Anything marked **⚠ WRITE** changes data: run it inside `BEGIN; ... ;` first, check the row count, then `COMMIT;`.

## Table of contents
1. [Architecture facts that matter during incidents](#1-architecture-facts-that-matter-during-incidents)
2. [Environment variable checklist](#2-environment-variable-checklist)
3. [Deployment procedure](#3-deployment-procedure)
4. [Zero-downtime migration procedure](#4-zero-downtime-migration-procedure)
5. [Rollback](#5-rollback)
6. [PostgreSQL backup & restore](#6-postgresql-backup--restore)
7. [Incident playbooks](#7-incident-playbooks)
8. [Known gaps](#8-known-gaps-fix-these)

---

## 1. Architecture facts that matter during incidents

| Fact | Consequence |
|---|---|
| Double-booking is prevented by partial unique index `uq_reservations_staff_active_slot (staff_id, start_time) WHERE status IN ('PENDING','CONFIRMED')`. | It only blocks the **same start time**. *Overlapping* ranges with different `start_time` are not blocked by the DB; the service layer must reject them. |
| Scheduled jobs use **ShedLock on PostgreSQL** (`shedlock` table), not Redis. | "Lock contention" for jobs is a Postgres matter. Jobs: `expirePendingReservations` (every minute), `sendReminders` (hourly), `markNoShows` (every 15 min). |
| Redis/Redisson are on the classpath, but no application code currently takes Redisson locks or uses Redis caches. | A Redis outage affects `/ready` (readiness group includes `redis`) but not booking correctness today. See incident 7.3. |
| Stripe webhooks are processed **synchronously** in one DB transaction; idempotency key is `stripe_events.event_id` (unique). | There is no internal queue. A "backlog" is Stripe retrying failed deliveries. |
| Email is sent synchronously with 3 attempts (1s, 2s backoff); final failure is written to `notification_log` as `FAILED`. | Failed emails are **not retried automatically afterwards**. |
| If no `JavaMailSender` bean exists, email is **simulated** and logged as `SENT`. | `SENT` in `notification_log` does not prove delivery unless SMTP is configured. |
| Flyway runs on application start **and** in the CI `flyway-migration` job. | Migrations can be applied by whichever runs first; both are safe (Flyway locks the schema history table). |
| Health: `/health`, `/ready`, `/actuator/health` (liveness + readiness groups). Metrics: `/actuator/prometheus`. | Custom metrics: `booking_success_total`, `booking_conflict_total`, `payment_failure_total`, `active_reservations`, `availability_latency`. |

---

## 2. Environment variable checklist

Source of truth: `.env.example` + `docker-compose.yml` (`app.environment`). Tick every row before a production deploy.

### Required in production

| ☐ | Variable | Used for | Production requirement |
|---|---|---|---|
| ☐ | `SPRING_PROFILES_ACTIVE` | Profile | `prod` |
| ☐ | `SPRING_DATASOURCE_URL` | App DB (compose builds this from `DB_NAME`) | `jdbc:postgresql://<host>:5432/<db>`; add `?sslmode=require` for managed DBs |
| ☐ | `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | App DB | Dedicated app role (not `postgres`); strong password |
| ☐ | `APPLICATION_JWT_SECRET` | JWT HMAC signing (set from `JWT_SECRET` in compose) | ≥ 32 chars, random. **The code has a built-in default; never deploy without setting it.** |
| ☐ | `APPLICATION_JWT_ACCESS_EXPIRATION_MINUTES` | Access token TTL | default `15` |
| ☐ | `APPLICATION_JWT_REFRESH_EXPIRATION_DAYS` | Refresh token TTL | default `7` |
| ☐ | `APPLICATION_STRIPE_API_KEY` | Stripe secret key (`sk_live_…`) | **See [Known gaps §8](#8-known-gaps-fix-these): name mismatch with compose** |
| ☐ | `APPLICATION_STRIPE_WEBHOOK_SECRET` | Webhook signature verification (`whsec_…`) | **Must be a real secret**; blank/`mock…` values disable signature checks |
| ☐ | `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` | SMTP | Real provider (587/465) |
| ☐ | `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | SMTP auth | Provider credentials |
| ☐ | `SPRING_DATA_REDIS_HOST` / `_PORT` / `_PASSWORD` | Redis (readiness probe) | Password mandatory |
| ☐ | `APPLICATION_REDISSON_ADDRESS` | Redisson | `redis://host:6379` (`rediss://` for TLS) |

### Recommended

| ☐ | Variable | Notes |
|---|---|---|
| ☐ | `OTEL_EXPORTER_OTLP_ENDPOINT` | Tracing collector. Default `http://localhost:4318/v1/traces` is wrong off-box. |
| ☐ | `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` | Lower from `1.0` to `0.1` under load. |
| ☐ | `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` | `health,metrics,prometheus,info`. `/actuator/**` is `permitAll`: **restrict at the ingress/network layer**. |
| ☐ | `LOG_LEVEL_ROOT` / `LOG_LEVEL_APP` | `INFO` / `INFO` in prod (default app level is `DEBUG`). |
| ☐ | `APPLICATION_JOBS_EXPIRE_PENDING_CRON`, `…_SEND_REMINDERS_CRON`, `…_MARK_NO_SHOWS_CRON` | Optional schedule overrides (defaults: `0 * * * * *`, `0 0 * * * *`, `0 */15 * * * *`). |

### CI/CD secrets (GitHub → Settings → Secrets)

`PROD_DATABASE_URL`, `PROD_DB_USERNAME`, `PROD_DB_PASSWORD`: used by the Flyway job. If unset, the job migrates a throwaway local Postgres (validation only). Use **separate** secrets per environment so a `dev` push cannot touch production.

### Pre-deploy verification

```bash
# No default/placeholder secrets left
env | grep -E 'JWT_SECRET|WEBHOOK_SECRET|STRIPE_API_KEY' | grep -Ei 'change-me|mock|test' && echo "STOP: placeholder secret"
# App can reach dependencies
curl -fsS https://<host>/ready | jq .
```

---

## 3. Deployment procedure

1. **Green CI on the commit** (lint → test → package → image push → Flyway → deploy).
2. **Take a backup** if the release contains a migration ([§6](#6-postgresql-backup--restore)).
3. **Note the currently running image tag** (needed for rollback): `docker inspect reservation-app --format '{{.Config.Image}}'`.
4. Deploy with rolling replacement (≥ 2 instances behind the load balancer; new instance must pass `/ready` before the old one drains).
5. **Smoke test:**
   ```bash
   curl -fsS https://<host>/health
   curl -fsS https://<host>/actuator/prometheus | grep -E 'booking_success_total|payment_failure_total'
   ```
6. **Watch for 15 min:** 5xx rate, `booking_conflict_total` rate, `payment_failure_total`, DB connection count, and `notification_log` `FAILED` rows.

---

## 4. Zero-downtime migration procedure

During a rolling deploy, **old and new application versions run against the same schema at the same time**. Every migration must therefore be compatible with the *previous* release. Use **expand → migrate → contract**, each stage a separate release.

### Rules for a safe migration file

| Do | Don't |
|---|---|
| `ADD COLUMN … NULL` (or with a constant `DEFAULT`; instant on PG ≥ 11) | `ADD COLUMN … NOT NULL` without default |
| `CREATE INDEX CONCURRENTLY` (see below) | Plain `CREATE INDEX` on a large live table (blocks writes) |
| `ADD CONSTRAINT … NOT VALID`, then `VALIDATE CONSTRAINT` later | Adding `CHECK`/`FK` that scans the table under lock |
| Add new tables/columns that old code ignores | Rename or drop a column/table old code still reads |
| Backfill in batches (≤ 5–10k rows, commit between) | One giant `UPDATE` |
| Set `SET lock_timeout = '5s';` at top of the migration | Waiting indefinitely behind a long transaction |

### Staged example: replace `reservations.notes` with `customer_notes`

| Release | Migration | App behavior |
|---|---|---|
| **N (expand)** | `V6__add_customer_notes.sql`: `ALTER TABLE reservations ADD COLUMN customer_notes TEXT;` | Writes **both** columns; reads old column |
| **N+1 (migrate)** | Batched backfill (script or job), then verify `SELECT count(*) FROM reservations WHERE customer_notes IS NULL AND notes IS NOT NULL;` = 0 | Reads new column; still writes both |
| **N+2 (contract)** | `V7__drop_notes.sql`: `ALTER TABLE reservations DROP COLUMN notes;` | Only new column. Do this **only after** release N+1 is fully rolled out and rollback to N is no longer needed |

### Concurrent index creation

Flyway runs each migration in a transaction by default, and `CREATE INDEX CONCURRENTLY` cannot run in one. Put it in its own file with a Flyway script config:

`src/main/resources/db/migration/V8__idx_example.sql.conf`
```
executeInTransaction=false
```
`V8__idx_example.sql`
```sql
SET lock_timeout = '5s';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_example ON reservations (business_id, created_at);
```
If it fails midway it leaves an `INVALID` index: `DROP INDEX CONCURRENTLY idx_example;` then rerun.

### Procedure

1. **Review:** the PR must state which stage (expand/migrate/contract) the migration is, and confirm the previous release works against the new schema.
2. **Rehearse** on a restored copy of production data ([§6](#6-postgresql-backup--restore)): `./mvnw flyway:migrate -Dflyway.url=… ` and time it.
3. **Pre-check blockers** (long transactions that would stall `ALTER`):
   ```sql
   SELECT pid, now() - xact_start AS age, state, left(query, 80)
   FROM pg_stat_activity
   WHERE xact_start IS NOT NULL AND now() - xact_start > interval '30 seconds'
   ORDER BY age DESC;
   ```
4. **Backup** ([§6](#6-postgresql-backup--restore)).
5. **Apply** via the CI Flyway job (or `flyway:migrate`) **before** rolling out the new app image. The `expand` step is backward compatible, so old pods keep serving.
6. **Verify:**
   ```sql
   SELECT installed_rank, version, description, success, installed_on
   FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;
   ```
   `success` must be `true` for every row.
7. Roll out the new app image ([§3](#3-deployment-procedure)).

### Failed migration

Flyway marks a failed migration `success = false` and refuses to start until repaired. PostgreSQL DDL is transactional, so a failed file normally leaves no partial changes (except `CONCURRENTLY` operations).
```bash
# 1. Confirm nothing was half-applied, fix the SQL (never edit an already-applied versioned file)
# 2. Remove the failed history row
./mvnw flyway:repair -Dflyway.url=… -Dflyway.user=… -Dflyway.password=…
# 3. Re-run
./mvnw flyway:migrate …
```

---

## 5. Rollback

Flyway Community has **no undo migrations**. Rollback = roll back the app, and roll the schema **forward** with a corrective migration. Restore from backup only as a last resort.

### 5.1 Application-only rollback (no migration in the release, or expand-only migration)

```bash
# Redeploy the previous image tag recorded in §3 step 3
docker compose pull && IMAGE_TAG=<previous-sha> docker compose up -d app
curl -fsS https://<host>/ready
```
Safe because expand-only migrations are backward compatible.

### 5.2 Release included a migration

| Situation | Action |
|---|---|
| Migration was additive (new nullable column/table/index) | Roll back the app only. Leave the schema; it is harmless. Fix forward in the next release. |
| Migration was a **contract** step (dropped/renamed) and old code breaks | **Fix forward** with a new migration `V{n+1}` that re-adds the column/table, then roll back the app. Data in dropped columns needs a backup restore (5.3). |
| Migration corrupted/lost data | Stop writes (scale app to 0 or maintenance page), restore per 5.3, then re-apply Stripe events (incident 7.4). |

### 5.3 Data rollback (last resort)

1. Scale the app to **0** to stop writes.
2. Restore into a **new** database ([§6.3](#63-restore)), validate, then repoint `SPRING_DATASOURCE_URL`.
3. Note the restore point timestamp. Data after it is lost: reconcile payments against Stripe (incident 7.4) and notify affected customers.

---

## 6. PostgreSQL backup & restore

### 6.1 Strategy

| Layer | Method | Frequency | Retention |
|---|---|---|---|
| Logical dump | `pg_dump -Fc` | Daily + **before every migration** | 14 days (daily), 90 days (monthly) |
| Physical / PITR | Managed-DB snapshots + WAL archiving (or `pg_basebackup` + `archive_command`) | Continuous | 7 days |
| Off-site copy | Upload dump to object storage (encrypted) | With each dump | Same |
| **Restore test** | Restore latest dump to scratch DB, run smoke queries | Monthly | n/a |

An untested backup is not a backup.

### 6.2 Backup

```bash
# Docker Compose deployment (container name from docker-compose.yml)
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
docker exec reservation-postgres \
  pg_dump -U "$DB_USERNAME" -d "$DB_NAME" -Fc --no-owner \
  > "reservation_${DB_NAME}_${STAMP}.dump"

# Integrity check: must list the tables without error
pg_restore --list "reservation_${DB_NAME}_${STAMP}.dump" | head
sha256sum "reservation_${DB_NAME}_${STAMP}.dump" > "reservation_${DB_NAME}_${STAMP}.dump.sha256"
```
`-Fc` custom format is compressed and allows selective/parallel restore.

### 6.3 Restore

```bash
# 1. Create a NEW database. Do not restore over the live one.
docker exec reservation-postgres psql -U "$DB_USERNAME" -c "CREATE DATABASE reservation_restore;"

# 2. Restore (use -j for parallelism on big dumps)
cat reservation_<stamp>.dump | docker exec -i reservation-postgres \
  pg_restore -U "$DB_USERNAME" -d reservation_restore --no-owner --exit-on-error

# 3. Validate
docker exec reservation-postgres psql -U "$DB_USERNAME" -d reservation_restore -c "
  SELECT 'reservations' t, count(*) FROM reservations
  UNION ALL SELECT 'payments', count(*) FROM payments
  UNION ALL SELECT 'stripe_events', count(*) FROM stripe_events;
  SELECT max(version) latest_flyway FROM flyway_schema_history WHERE success;
  SELECT max(created_at) newest_reservation FROM reservations;"

# 4. Cut over: stop app, point SPRING_DATASOURCE_URL at reservation_restore
#    (or rename databases during a maintenance window), start app, check /ready
```

### 6.4 Single-table recovery (e.g. accidentally deleted rows)

```bash
pg_restore -U "$DB_USERNAME" -d reservation_restore -t reservations --data-only <dump>
# then INSERT … SELECT only the missing rows into production in a transaction
```

### 6.5 Post-restore checklist

- ☐ `flyway_schema_history` matches the app version about to start
- ☐ `shedlock` rows are stale: harmless (`lock_until` in the past), or `DELETE FROM shedlock;` to be safe
- ☐ Re-run Stripe reconciliation (7.4) for the gap between restore point and now
- ☐ Reminder/expiry jobs will catch up on their own at next cron tick

---

## 7. Incident playbooks

Format: **Symptom → Diagnosis → Fix → Prevention**.

---

### 7.1 Duplicate bookings appearing

**Symptom:** Two customers report the same staff member at the same time; a calendar shows two active reservations that collide; `booking_conflict_total` is flat while complaints rise.

**Why it can happen here:** the unique index `uq_reservations_staff_active_slot` blocks identical `(staff_id, start_time)` only. Different start times that **overlap** (e.g. 10:00–11:00 and 10:30–11:30) are not caught by the DB.

**Diagnosis**

```sql
-- A. Exact duplicates (should return 0 rows: the unique index forbids it).
--    Rows here mean the index is missing or INVALID: check D.
SELECT staff_id, start_time, count(*)
FROM reservations
WHERE status IN ('PENDING','CONFIRMED')
GROUP BY staff_id, start_time HAVING count(*) > 1;

-- B. Overlapping active reservations for the same staff (the real risk)
SELECT a.id AS res_a, b.id AS res_b, a.staff_id,
       a.start_time AS a_start, a.end_time AS a_end,
       b.start_time AS b_start, b.end_time AS b_end,
       a.status AS a_status, b.status AS b_status, a.created_at AS a_created, b.created_at AS b_created
FROM reservations a
JOIN reservations b
  ON a.staff_id = b.staff_id
 AND a.id < b.id
 AND a.start_time < b.end_time AND b.start_time < a.end_time
WHERE a.status IN ('PENDING','CONFIRMED')
  AND b.status IN ('PENDING','CONFIRMED')
ORDER BY a.start_time;

-- C. Stale PENDING holds blocking/confusing slots (expiry job should clear these every minute)
SELECT id, staff_id, start_time, hold_expires_at, now() - hold_expires_at AS overdue
FROM reservations
WHERE status = 'PENDING' AND hold_expires_at < now()
ORDER BY hold_expires_at LIMIT 50;

-- D. Is the safety-net index present and valid?
SELECT i.indexrelid::regclass AS index, i.indisvalid, i.indisunique
FROM pg_index i WHERE i.indexrelid::regclass::text = 'uq_reservations_staff_active_slot';

-- E. Did expiry job run? (lock_until/locked_at show last execution)
SELECT * FROM shedlock WHERE name = 'expirePendingReservations';
```

**Fix**

1. **Stop the bleeding:** if B returns new rows continuously, put the booking endpoint behind maintenance or scale to a single instance while investigating.
2. **Index missing/invalid (D)**: recreate (needs duplicates resolved first):
   ```sql
   DROP INDEX CONCURRENTLY IF EXISTS uq_reservations_staff_active_slot;
   CREATE UNIQUE INDEX CONCURRENTLY uq_reservations_staff_active_slot
     ON reservations (staff_id, start_time) WHERE status IN ('PENDING','CONFIRMED');
   ```
3. **Resolve each conflicting pair from B (⚠ WRITE)**: keep the earlier `created_at` (or the paid one), cancel the other through the **application API** (so refund + notification events fire), not by raw `UPDATE`. Only if the API is unavailable:
   ```sql
   BEGIN;
   UPDATE reservations SET status = 'CANCELLED', updated_at = now()
   WHERE id = '<loser-id>' AND status IN ('PENDING','CONFIRMED');   -- expect 1 row
   COMMIT;
   ```
   Then manually refund in Stripe if a `payments` row exists (`SELECT * FROM payments WHERE reservation_id = '<loser-id>'`) and email the customer.
4. **C returns old PENDING rows:** the job is not running → see 7.3 (stuck ShedLock).

**Prevention:** add an overlap guard at DB level: `btree_gist` exclusion constraint on `(staff_id WITH =, tstzrange(start_time, end_time) WITH &&) WHERE status IN ('PENDING','CONFIRMED')`, shipped as an expand-style migration (create `NOT VALID` is not supported for exclusion constraints, so clean data first). Alert on query B returning rows.

---

### 7.2 Webhook backlog (Stripe retries piling up)

**Symptom:** Stripe dashboard → Developers → Webhooks shows failing deliveries/rising retry count; reservations stay `PENDING` after customers paid; `stripe_events` stops growing.

**Why:** webhooks are handled synchronously. If the endpoint returns non-2xx (signature failure, DB down, exception in handler), **nothing is recorded** (the `stripe_events` insert is in the same transaction) and Stripe retries for up to 3 days with exponential backoff, then gives up.

**Diagnosis**

```sql
-- Event ingestion rate (should be steady during business hours)
SELECT date_trunc('minute', created_at) AS minute, count(*), count(*) FILTER (WHERE event_type = 'payment_intent.succeeded') AS succeeded
FROM stripe_events WHERE created_at > now() - interval '1 hour' GROUP BY 1 ORDER BY 1 DESC LIMIT 30;

-- Last event processed
SELECT max(created_at) AS last_event, now() - max(created_at) AS silence FROM stripe_events;

-- Payments that never got their webhook (customer likely paid; webhook missing)
SELECT p.id, p.reservation_id, p.stripe_payment_intent_id, p.status, p.created_at, r.status AS reservation_status
FROM payments p JOIN reservations r ON r.id = p.reservation_id
WHERE p.status = 'REQUIRES_PAYMENT_METHOD' AND r.status = 'PENDING'
  AND p.created_at < now() - interval '15 minutes'
ORDER BY p.created_at;
```
```bash
# App side: why is it failing?
docker logs reservation-app --since 30m 2>&1 | grep -E "Stripe|Invalid Stripe signature|Unhandled server exception"
```

| Log line | Meaning |
|---|---|
| `Invalid Stripe signature` / `Stripe signature verification failed` | Wrong `whsec_…` (rotated secret, test vs live mismatch), or a proxy altered the raw body |
| `Unhandled server exception` | Handler bug or DB down: read the stack trace |
| `No matching payment record found for PaymentIntent` | Event for an intent not in `payments`: usually test-mode event hitting prod, ignore |
| `Duplicate Stripe event received` | Normal (idempotent no-op) |

**Fix**

1. Fix the root cause (correct `APPLICATION_STRIPE_WEBHOOK_SECRET`, restore DB, deploy bug fix), then restart.
2. **Replay missed events**: in Stripe Dashboard → Webhooks → endpoint → *Resend* failed events, or:
   ```bash
   stripe events list --type payment_intent.succeeded --limit 100          # find ids
   stripe events resend evt_XXXX --webhook-endpoint we_XXXX
   ```
   Replays are safe: duplicates hit `existsByEventId` and no-op.
3. If Stripe has already given up (> 3 days), reconcile by hand (see 7.4 step 3).

**Prevention:** alert on `now() - max(stripe_events.created_at)` > 30 min during business hours and on Stripe's "webhook failing" email; ensure the ingress forwards the **raw** request body and the `Stripe-Signature` header unchanged.

---

### 7.3 Lock contention (Redis and job locks)

**Symptom:** scheduled jobs stop running (stale `PENDING` holds, no reminders, no no-show marking), or `/ready` is DOWN with Redis listed, or requests slow with many connections waiting.

> **Today, job locking is ShedLock on Postgres** and the application does not take Redisson locks. Triage both layers.

**Diagnosis: ShedLock (jobs)**

```sql
SELECT name, locked_by, locked_at, lock_until,
       lock_until > now() AS currently_held,
       now() - locked_at AS held_for
FROM shedlock ORDER BY name;
-- Expected names: expirePendingReservations, sendReminders, markNoShows
-- Suspicious: currently_held = true for far longer than the job's runtime
--  (lockAtMostFor: expire=5m, reminders=30m, noShows=10m)
```
If `lock_until` is in the past but the job still isn't running, check the scheduler itself:
```bash
docker logs reservation-app --since 30m 2>&1 | grep "Scheduled job completed"
```
No lines → the cron isn't firing (app down, `application.jobs.*.cron` misconfigured, or all instances paused).

**Diagnosis: Postgres contention**

```sql
-- Who is blocking whom
SELECT blocked.pid AS blocked_pid, left(blocked.query, 60) AS blocked_query,
       blocking.pid AS blocking_pid, left(blocking.query, 60) AS blocking_query,
       now() - blocking.xact_start AS blocking_age
FROM pg_stat_activity blocked
JOIN pg_stat_activity blocking ON blocking.pid = ANY (pg_blocking_pids(blocked.pid))
WHERE cardinality(pg_blocking_pids(blocked.pid)) > 0;
```

**Diagnosis: Redis**

```bash
redis-cli -h $REDIS_HOST -a "$REDIS_PASSWORD" ping
redis-cli -h $REDIS_HOST -a "$REDIS_PASSWORD" info clients | grep -E 'connected_clients|blocked_clients'
redis-cli -h $REDIS_HOST -a "$REDIS_PASSWORD" --scan --pattern '*lock*' | head   # any lock keys (should be few/none today)
redis-cli -h $REDIS_HOST -a "$REDIS_PASSWORD" slowlog get 10
```

**Fix**

1. **Stuck ShedLock row** (instance crashed holding it; it self-heals at `lock_until`, but to release now): **⚠ WRITE**
   ```sql
   UPDATE shedlock SET lock_until = now() WHERE name = 'expirePendingReservations';
   ```
   Next cron tick will acquire it. Never `DELETE` while a job is genuinely mid-run: two instances could then run it (the job is idempotent-ish, but avoid).
2. **Blocking Postgres session:** `SELECT pg_cancel_backend(<blocking_pid>);` (graceful), escalate to `pg_terminate_backend` only if it persists. Identify why the transaction is long-lived.
3. **Redis down/slow:** restart/fail over Redis. The app will report not-ready until reconnect; booking correctness does not depend on Redis today. If `/ready` flapping removes all pods from the load balancer, temporarily remove `redis` from `management.endpoint.health.group.readiness.include` rather than serving 503s.
4. **Stale lock keys in Redis** (only if future code adds Redisson locks): `redis-cli DEL <key>` after confirming the owner is gone.

**Prevention:** alert on `now() - max(locked_at)` per job vs. its schedule; keep transactions short; set `lock_timeout` / `idle_in_transaction_session_timeout` (e.g. `60s`) on the app role.

---

### 7.4 Stripe payment stuck in PENDING

**Symptom:** customer paid (card charged) but reservation remains `PENDING`, or is expired and cancelled despite payment; or payment row sits in `REQUIRES_PAYMENT_METHOD`.

**Status model:** `payments.status` ∈ `REQUIRES_PAYMENT_METHOD` → `SUCCEEDED` | `FAILED`. A reservation becomes `CONFIRMED` only when `payment_intent.succeeded` is processed while the reservation is still `PENDING`. A `PENDING` reservation past `hold_expires_at` is cancelled by `expirePendingReservations`.

**Diagnosis**

```sql
-- A. Candidates: payment not finished, reservation still held
SELECT r.id AS reservation_id, r.status, r.hold_expires_at, p.status AS payment_status,
       p.stripe_payment_intent_id, p.amount, p.created_at,
       now() - p.created_at AS age
FROM reservations r JOIN payments p ON p.reservation_id = r.id
WHERE r.status = 'PENDING' AND p.created_at < now() - interval '10 minutes'
ORDER BY p.created_at;

-- B. Did we ever receive an event for this intent?
SELECT event_id, event_type, status, created_at
FROM stripe_events
WHERE payload LIKE '%<pi_XXXX>%' ORDER BY created_at;

-- C. Paid but reservation was expired/cancelled: money taken, no booking (worst case)
SELECT r.id, r.status, r.hold_expires_at, p.status AS payment_status, p.stripe_payment_intent_id
FROM reservations r JOIN payments p ON p.reservation_id = r.id
WHERE p.status = 'SUCCEEDED' AND r.status IN ('CANCELLED');

-- D. Failed attempts for this customer/intent
SELECT * FROM payments WHERE stripe_payment_intent_id = '<pi_XXXX>';
```
Then check Stripe: `stripe payment_intents retrieve pi_XXXX` → `status` (`succeeded`, `requires_payment_method`, `requires_action`, `processing`).

**Decision table**

| Stripe says | DB says | Cause | Action |
|---|---|---|---|
| `succeeded` | `REQUIRES_PAYMENT_METHOD`, no event in `stripe_events` | Webhook missed | Resend event (7.2 step 2). If impossible, manual fix below |
| `succeeded` | `SUCCEEDED`, reservation `CANCELLED` | Hold expired before webhook arrived | Re-confirm if slot is free, else **refund** |
| `requires_payment_method` / `requires_action` | `REQUIRES_PAYMENT_METHOD` | Customer abandoned / 3-DS pending | Nothing: hold expiry will release the slot |
| `requires_payment_method` with `last_payment_error` | `FAILED` | Card declined | Customer retries; `PaymentFailedEvent` email already sent |
| `processing` | any | Async method (bank debit) | Wait for event; do not intervene |

**Fix: manual confirmation (⚠ WRITE, only after Stripe confirms `succeeded` and webhook replay failed)**

```sql
BEGIN;
UPDATE payments SET status = 'SUCCEEDED', updated_at = now()
WHERE stripe_payment_intent_id = '<pi_XXXX>' AND status <> 'SUCCEEDED';       -- expect 1

UPDATE reservations SET status = 'CONFIRMED', updated_at = now()
WHERE id = '<reservation-id>' AND status = 'PENDING';                         -- expect 1
-- Re-check no overlap before COMMIT (query B in 7.1) for that staff_id/time
COMMIT;
```
A raw update skips the confirmation email/event: send the confirmation manually, or prefer the webhook replay which runs the real code path.

**Fix: expired but paid**: if the slot is still free, set `CONFIRMED` as above (it is blocked by the unique index if someone else took it); otherwise refund: `stripe refunds create --payment-intent pi_XXXX`, set `payments.status`/`stripe_refund_id`, and email the customer.

**Bulk reconciliation after a long outage**
```bash
stripe payment_intents list --created.gte=<epoch> --limit 100 | jq -r '.data[] | select(.status=="succeeded") | .id' > succeeded.txt
```
Compare against `SELECT stripe_payment_intent_id FROM payments WHERE status='SUCCEEDED'`; the difference is your repair list.

**Prevention:** shorten webhook failure detection (7.2 alerts); consider extending `hold_expires_at` only when a PaymentIntent is `processing`; schedule a daily reconcile job.

---

### 7.5 Email delivery failures

**Symptom:** customers don't get confirmation/reminder/cancellation emails; `notification_log` shows `FAILED`; logs show `All 3 retry attempts exhausted`.

**Diagnosis**

```sql
-- Failure rate by hour and template
SELECT date_trunc('hour', sent_at) AS hour, template,
       count(*) FILTER (WHERE status = 'SENT')   AS sent,
       count(*) FILTER (WHERE status = 'FAILED') AS failed
FROM notification_log WHERE sent_at > now() - interval '24 hours'
GROUP BY 1, 2 ORDER BY 1 DESC, 2;

-- Top error messages
SELECT left(error_message, 120) AS error, count(*), min(sent_at), max(sent_at)
FROM notification_log WHERE status = 'FAILED' AND sent_at > now() - interval '24 hours'
GROUP BY 1 ORDER BY 2 DESC;

-- Which reservations lost a notification (candidates for resend)
SELECT nl.reservation_id, nl.template, nl.recipient, nl.sent_at, left(nl.error_message, 80) AS error
FROM notification_log nl
WHERE nl.status = 'FAILED' AND nl.sent_at > now() - interval '24 hours'
  AND NOT EXISTS (SELECT 1 FROM notification_log ok
                  WHERE ok.reservation_id = nl.reservation_id AND ok.template = nl.template
                    AND ok.recipient = nl.recipient AND ok.status = 'SENT');

-- Reminders: confirmed bookings that should have had one
SELECT count(*) FROM reservations
WHERE status = 'CONFIRMED' AND reminder_sent_at IS NULL AND start_time < now() + interval '24 hours' AND start_time > now();
```

**Is it really sending?** If there are many `SENT` rows but customers get nothing, check whether SMTP is configured: with no `JavaMailSender` bean the app **simulates** delivery and logs `SENT`:
```bash
docker logs reservation-app 2>&1 | grep -c "Simulated email delivery"      # > 0 = SMTP not configured
docker logs reservation-app 2>&1 | grep -E "Email delivered via SMTP" | tail -3
```

| Error text | Likely cause | Fix |
|---|---|---|
| `Simulated email delivery … (SMTP not configured)` | `SPRING_MAIL_HOST` unset | Set `SPRING_MAIL_*` and restart |
| `AuthenticationFailedException` / `535` | Bad SMTP credentials | Rotate `SPRING_MAIL_USERNAME/PASSWORD` |
| `Could not connect to SMTP host` / timeout | Host/port/firewall/TLS (587 STARTTLS vs 465 SSL) | Verify with `openssl s_client -starttls smtp -connect host:587` |
| `550` / `553` sender rejected | `MAIL_FROM` domain lacks SPF/DKIM or isn't verified | Fix DNS records / verified sender |
| `Could not parse mail` / template errors | Thymeleaf template problem | Check `src/main/resources/templates/email/` |
| Recipient `5.1.1` | Invalid customer address | Ignore; contact customer otherwise |

**Fix**

1. Fix the root cause above and restart.
2. **Failed rows are not retried automatically** after the 3 in-process attempts. Resend manually using the list from the third query (re-trigger from the app/admin API, e.g. re-save the reservation event, or send an ad-hoc message). Do **not** bulk-edit `notification_log`; it is an audit trail.
3. Missed reminders: clear the flag so the hourly job picks them up again (⚠ WRITE, only for reservations whose reminder window hasn't passed):
   ```sql
   UPDATE reservations SET reminder_sent_at = NULL
   WHERE id IN (<ids>) AND status = 'CONFIRMED' AND start_time > now();
   ```
   (Before doing so, check whether `sendReminders` sets `reminder_sent_at` before or after the send succeeds, so you don't resend duplicates.)

**Prevention:** alert when `FAILED / (SENT+FAILED)` > 5% over 30 min; use a transactional provider (SES/Postmark/SendGrid) with bounce webhooks; monitor SPF/DKIM/DMARC.

---

## 8. Known gaps (fix these)

Found while writing this runbook; they directly affect incidents above.

1. **Stripe env var names don't bind.** The code reads `application.stripe.api-key` and `application.stripe.webhook-secret` (→ env `APPLICATION_STRIPE_API_KEY`, `APPLICATION_STRIPE_WEBHOOK_SECRET`), but `docker-compose.yml` sets `STRIPE_API_KEY` / `STRIPE_WEBHOOK_SECRET`, which Spring does not map to those properties. The app silently falls back to `mock-stripe-key` / `mock-webhook-secret`.
2. **Webhook signature verification is skipped for mock/blank secrets.** `StripeServiceImpl.processWebhookEvent` accepts any payload when the secret is blank or starts with `mock`. Combined with gap 1, a misconfigured production deployment accepts **unauthenticated** webhooks at `/api/webhooks/stripe` (`permitAll`), so anyone could mark a reservation paid. Fail startup in the `prod` profile if the secret is missing or mock.
3. **`/actuator/**` is `permitAll`.** Restrict at ingress or add a management port.
4. **No overlap constraint at DB level** (see 7.1 prevention).
5. **`JWT` secret has a default in code** (`application.jwt.secret`); fail fast in `prod` if unset.
