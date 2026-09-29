# CommerceCore Handoff Context

## Current position

- Repository: `C:\Users\chitk\Downloads\CommerceCore_S001C_Phase1_Final\CommerceCore`
- Phase 9 - Kafka + Redis + Microservices + Observability: **PASS**.
- Phase 8 verified baseline was 76 tests, PostgreSQL 16, Flyway V1-V12,
  Hibernate validation and runtime smoke all green.
- Next boundary: Phase 10 - Frontend + Integration + Testing + Deployment.
- Do not restart Phase 9 or begin Phase 10 without an explicit task.

## Phase 9 architecture

- CommerceCore remains the main modular monolith and authority for users,
  catalog, inventory, checkout, orders, payments/refunds, reviews, admin,
  alerts, audit, and reports. Payment/order/inventory were not extracted and
  their PostgreSQL locking/idempotency rules remain authoritative.
- One low-coupling microservice was extracted: Notification Service. It owns
  its own PostgreSQL database, its own Flyway history, Kafka consumption,
  durable `processed_events`, notification records, and the existing customer
  notification API.
- API Gateway is the external entry point: notification paths route to the
  service and other `/api/**` paths route to core. It validates/generates
  `X-Correlation-Id` and strips client-supplied internal identity headers.
  Both downstream services validate the shared JWT independently.
- Shared event contracts use JSON `EventEnvelope`: eventId UUID, eventType,
  eventVersion, occurredAt, correlationId, aggregateType, aggregateId, payload.
  No JPA entity is serialized.

## Kafka and delivery

- Topics: `commercecore.user.events.v1`, `commercecore.order.events.v1`,
  `commercecore.payment.events.v1`, `commercecore.inventory.events.v1`,
  `commercecore.review.events.v1`, and
  `commercecore.notification.events.dlq`.
- Aggregate IDs are stable Kafka keys. Producers use `acks=all` and idempotence.
- Guarantee is **at least once**, not distributed exactly once. Duplicate
  transport delivery is expected and durable DB idempotency prevents duplicate
  notification effects.
- Core V13 `outbox_events` is written in the same transaction as business
  changes. Publisher batches use `FOR UPDATE SKIP LOCKED`, wait for Kafka
  confirmation before marking published, retain attempts/errors/backoff, keep
  history, and clean only old published records after configurable retention.
- Notification consumption atomically creates a record and inserts event ID
  into `processed_events`. Malformed and unsupported-version events do not
  mutate state and are routed after bounded retry/non-retry classification to
  the DLQ with Kafka origin/error metadata.
- V1 compatibility: additive fields are allowed and ignored by tolerant
  consumers; semantic breaking changes require a new event version; unsupported
  versions go to DLQ.

## Redis cache

- Redis is a non-authoritative cache; PostgreSQL remains the source of truth.
- Cache-aside covers product detail and product rating summary.
- Keys are versioned/namespaced: `commercecore:v1:product:{slug}` and
  `commercecore:v1:rating:{productId}`. TTL is bounded, configurable, and
  jittered.
- Catalog mutation and review moderation evict affected entries only after DB
  commit. Redis errors increment metrics, log without sensitive payloads, and
  fall back to PostgreSQL for correct reads.

## Migrations and services

- Core: Flyway V1-V13; V13 creates transactional outbox and indexes.
- Notification Service: independent
  `V1__notification_service_baseline.sql` for `notifications` and
  `processed_events`.
- Compose services: core PostgreSQL 16, notification PostgreSQL 16, Apache Kafka
  3.9.1 in KRaft mode, Redis 7.4, CommerceCore Core, Notification Service,
  API Gateway, and Prometheus 3.5.
- Config is environment-driven through `.env.example`; no real secret was used
  or committed.

## Verification evidence

- Pre-change Phase 8 gate reran: 76 tests, 0 failures, 0 errors, 0 skipped.
- Final `mvnw test`: **87 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS
  in 5:17. Real PostgreSQL 16, Kafka and Redis Testcontainers executed.
- Phase 9 focused tests: `Phase9OutboxRedisIT` (4),
  `Phase9NotificationServiceIT` (4), and `Phase9GatewayObservabilityIT` (3).
- Proven: atomic outbox commit and rollback; confirmed Kafka publication;
  stable event key/version/correlation; consumer persistence; duplicate event
  suppression; ownership isolation; bounded retry and DLQ for poison JSON and
  unsupported version; Redis miss/hit/invalidation/fallback; gateway routing,
  correlation and malicious-header stripping; health and Prometheus metrics.
- Full runtime Compose stack was healthy. Port 8080 was occupied, so final
  gateway verification used the authorized temporary port 18080; core was 8082,
  Notification Service 8081, Prometheus 9090.
- HTTP 200: gateway/core/notification health and probes, all three Prometheus
  endpoints, core `/v3/api-docs`, core `/swagger-ui.html`, Prometheus health.
- Runtime gateway flow used sandbox data only: verify-email transaction created
  USER_REGISTERED in V13 outbox; Kafka published it; Notification Service
  stored exactly one WELCOME record in its isolated DB; gateway returned it;
  correlation `phase9-runtime-correlation-0001` survived in the outbox.
- Kafka stopped: password change returned 200 and one pending outbox row;
  restart published it and created one notification. Notification Service
  stopped: core password change returned 200 and Kafka published; restart
  caught up from 1 to 2 PASSWORD_CHANGED notifications. Redis stopped: warmed
  product read still returned 200 from PostgreSQL. Replaying USER_REGISTERED
  kept WELCOME count at one.
- Prometheus custom families include outbox publish/pending, notification Kafka
  processed/duplicate/retry/DLQ, notifications created, and Redis cache
  hit/miss/error. Tags do not contain business IDs.

## Known issue

- No critical issue. Full-suite shutdown emits noisy connection warnings and a
  Surefire forced-fork-shutdown note because cached scheduled Spring contexts
  briefly outlive per-class Testcontainers. It does not alter the verified
  result: 87/0/0/0 and BUILD SUCCESS.

## Useful next commands

```powershell
docker info
.\mvnw.cmd test
$env:GATEWAY_PORT='18080'; docker compose -p commercecore-phase9-verify ps
```
