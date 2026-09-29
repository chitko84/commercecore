# ARCHITECTURE.md

## Strategy

CommerceCore starts as a **modular monolith** and evolves toward selected
microservices only in Phase 9, once module boundaries have proven stable.

## Stack

- Java 21, Spring Boot 3.5.16, Maven (with Maven Wrapper)
- PostgreSQL 16 as the primary datastore
- Flyway owns all schema changes — no `hibernate.ddl-auto=update` in any
  environment beyond local scratch use (`ddl-auto: validate` everywhere)
- Docker / Docker Compose for local infra
- Spring Web (REST), Spring Data JPA, Spring Security, Bean Validation
- springdoc-openapi 2.8.17 for API docs, Spring Boot Actuator for health/metrics
- JUnit 5, Mockito, Testcontainers (via `spring-boot-testcontainers` /
  `@ServiceConnection`) for testing

## Module boundaries (current)

`com.commercecore.{auth, user, product, category, inventory, cart,
wishlist, promotion, order, payment, shipping, review, notification,
audit, report, common, config}`

- `common` — shared kernel: cross-cutting utilities/base types used by
  more than one module. Keep this small; don't dump unrelated code here.
- `config` — application-wide configuration (security, web, OpenAPI,
  persistence, etc.).
- All other packages are business modules. Each business module should
  eventually own its own controllers, services, repositories, entities,
  and DTOs, and should depend on other modules through well-defined
  interfaces/DTOs rather than reaching into another module's internals.

The `user` module owns accounts, profiles, addresses and their API DTOs. The
`auth` module owns registration, verification, login, JWT issuance, opaque
refresh sessions, password recovery and email delivery. The `category` module
owns the catalog hierarchy; the `product` module owns products, media metadata,
catalog querying and their APIs. Other business modules remain package
boundaries only.

## Rules all future work must follow

- REST APIs only between client and backend at this stage.
- **DTO boundaries**: JPA entities are never returned directly from
  controllers. Controllers accept/return DTOs; services translate
  between DTOs and entities.
- **Constructor injection** only — no field injection.
- **No business logic in controllers.** Controllers validate input, call
  a service, and map the result. Business rules live in the service
  layer.
- **Flyway owns schema changes.** Every schema change ships as a
  versioned Flyway migration; no ad hoc DDL.
- **No hardcoded secrets.** Configuration/secrets come from environment
  variables or a secrets store, never committed to source control.
- Kafka, Redis, and any microservice split are deliberately deferred to
  later phases (see `PRODUCT_REQUIREMENTS.md`) and must not be
  introduced early.

## Runtime/data/API foundation (Phase 1 / S001B)

- **Environment profiles**: `application.yml` (common) +
  `application-dev.yml` / `application-test.yml` / `application-prod.yml`.
  Active profile is chosen by the `SPRING_PROFILES_ACTIVE` env var — no
  profile is forced by default. `dev` has local defaults for non-secret
  connection values but requires `DB_PASSWORD`; `prod` has no defaults and
  fails fast if required env vars are missing; `test` has no fixed datasource
  (Testcontainers supplies one dynamically via `@ServiceConnection`
  where a real database is needed).
- **Docker Compose** (`docker-compose.yml`) runs PostgreSQL locally,
  reading credentials from environment variables (see `.env.example`).
  No real credentials are committed anywhere in the repo.
- **Flyway** migrations live under `src/main/resources/db/migration`.
  `V1__baseline.sql` is a deliberately harmless bootstrap migration (no
  business tables) that proves the pipeline works; future schema
  changes are new, sequentially-numbered migrations.
- **Standard API error model**: `common.web.ApiError` (record) +
  `common.web.GlobalExceptionHandler` (`@RestControllerAdvice`,
  extending `ResponseEntityExceptionHandler`). Every error response —
  validation, malformed request, not-found, unexpected server error —
  returns the same JSON shape and never a stack trace. `common.web
  .CorrelationIdFilter` attaches a per-request trace id (`X-Request-Id`
  header, SLF4J MDC) that shows up in both logs and `ApiError.traceId`.
- **Validation**: Jakarta Bean Validation via
  `spring-boot-starter-validation`; proven via a `src/test/java`-only
  controller/DTO, not a production endpoint.
- **OpenAPI/Swagger**: `config.OpenApiConfig` supplies minimal API
  metadata (title/description/version) for springdoc at
  `/swagger-ui.html` and `/v3/api-docs`.
- **Actuator**: only `/actuator/health` is exposed
  (`management.endpoints.web.exposure.include: health`), with details
  hidden (`never`) except in `dev`/`test`.
- **Security (`config.SecurityConfig`)**: stateless Bearer JWT validation,
  configured CORS, CSRF disabled for the token-based REST API, secure response
  headers, standardized 401/403 bodies, and route rules for account, staff and
  admin namespaces. Health and Swagger/OpenAPI remain public.
- **Logging**: request-scoped trace id in the log pattern
  (`logging.pattern.level` includes `%X{traceId}`); no credential/token
  values are ever logged.

## Users and authentication (Phase 2)

- `UserAccount` maps to PostgreSQL `users` with a generated UUID primary key,
  string-persisted roles/statuses, timestamps, and an optimistic-lock version.
- Roles are exactly `CUSTOMER`, `STAFF`, and `ADMIN`; account statuses are
  `PENDING_VERIFICATION`, `ACTIVE`, `LOCKED`, and `DEACTIVATED`.
- Email is immutable after creation, trimmed and lowercased with `Locale.ROOT`.
  V2 enforces canonical lowercase storage and a unique constraint, so email
  remains a case-insensitive login identifier without a PostgreSQL extension.
- Passwords use Spring Security's delegating BCrypt encoder. Entities never
  cross API boundaries and credential hashes never appear in response DTOs.
- Verification, password-reset and opaque refresh values are generated with a
  cryptographically secure RNG; only SHA-256 hashes are persisted. Refresh
  tokens rotate in families and reuse revokes the active family.
- HS256 access JWTs contain only user id, role, issue/expiry time and jti. The
  signing secret is external configuration and access lifetime defaults to 15
  minutes.
- Five failed logins temporarily lock an account for 20 minutes by default;
  both values are configurable. Manual locks remain distinguishable by a null
  `locked_until`, and deactivated accounts never auto-reactivate.
- Address ownership is enforced through user-scoped repository lookups. Partial
  PostgreSQL unique indexes allow at most one default shipping and one default
  billing address per user. Deleting a default address leaves no default until
  the client selects another.
- SMTP is the production delivery adapter. Explicit development mode writes
  messages to an ignored local outbox without logging raw token values.

## Product catalog and search (Phase 3)

- Categories use a simple adjacency-list parent relationship. Services reject
  self-parenting and cycles; deactivation cascades to descendants, while
  products are retained for history.
- Product SKU is trimmed and uppercased; product/category slugs are trimmed and
  lowercased. Application validation is backed by database canonical-form and
  unique constraints.
- Products use fixed-scale decimal prices. Effective price is `salePrice` when
  present and `basePrice` otherwise, consistently in responses, validation,
  filters and sorting.
- Product media stores URL/path metadata only. Ordering is explicit and a
  PostgreSQL partial unique index permits at most one primary item per product.
- Search uses composable Spring Data JPA Specifications over PostgreSQL. Public
  queries enforce active product/category state and support text, category
  subtree, brand and effective-price filters. A closed sort vocabulary prevents
  clients from injecting arbitrary persistence properties; page size is capped
  at 100.

## Variants and inventory (Phase 4)

- Variants belong to products and carry a canonical SKU, optional price
  override and ordered generic attributes. Attribute rows use name/value pairs;
  no product-specific attribute-definition engine is introduced.
- One inventory row owns exactly one product or one variant. Direct product
  inventory is valid only while a product has no variants; variant products use
  variant-owned stock. PostgreSQL XOR and partial unique constraints reinforce
  this boundary.
- Inventory accounting is `available = total - reserved - sold`; availability
  and stock status are derived. Quantity checks exist in both the domain and
  database.
- Mutation services are transactional and load inventory through
  `PESSIMISTIC_WRITE` (`SELECT ... FOR UPDATE`) before validation. This is the
  primary oversell defense; entity versions provide secondary protection.
- Reservation `(inventory, referenceKey)` uniqueness makes orchestration
  retries idempotent for matching ACTIVE/CONFIRMED reservations. Release,
  confirmation and explicit expiry are terminal transitions.
- History is append-only movement data with compact before/after counters,
  reference/reason/actor metadata and no update API. Public DTOs expose only
  derived stock status, never quantities, reservations or history.

## Cart, wishlist, and promotions (Phase 5)

- A customer has one lazily created persistent cart. Cart lines reference a
  product and optional variant; partial unique indexes prevent duplicate direct
  and variant selections. A shared sellable-item resolver enforces catalog,
  variant ownership, and current inventory rules.
- Cart and wishlist operations are read-only consumers of inventory. They do
  not create reservations or mutate stock. Cart reads surface stale-line state
  and checkout readiness instead of silently deleting unavailable selections.
- Cart pricing uses current effective catalog prices with scale-2 HALF_UP money
  arithmetic. It returns merchandise subtotal/discount only; taxes, shipping
  amount, and order totals remain outside Phase 5.
- Wishlist items are customer-owned selections without a redundant aggregate.
  Move-to-cart is one transaction: the wishlist row is removed only after the
  normal cart add succeeds.
- Promotions use fixed type/scope enums and explicit product/category targets.
  Category eligibility walks the adjacency-list ancestors, so a category coupon
  includes descendants. One applied promotion is re-evaluated on every cart read.
- Redemption records are separate from cart application. The internal
  pessimistically locked operation enforces total/per-user limits and an
  idempotent reference key for later checkout integration.

## Checkout, orders, and shipping (Phase 6)

- Checkout is a single transactional orchestration boundary: it revalidates
  the cart, snapshots mutable catalog/address/promotion data, reserves stock,
  and clears the cart only on commit. `Idempotency-Key` is unique per customer
  and paired with a deterministic request fingerprint.
- Orders begin PENDING with expiring inventory reservations. The internal
  confirmation hook confirms reservations and records promotion redemption;
  payment providers remain a Phase-7 concern. Pending cancellation/expiry
  releases reservations.
- Order items, attributes, addresses and promotion/shipping values are
  immutable checkout snapshots. A strict order state machine and append-only
  status history protect lifecycle transitions.
- Shipping uses configurable STANDARD/EXPRESS quotes and one shipment per
  order with immutable tracking events. Tax is a configurable percentage of
  discounted merchandise total.

## Payments and refunds (Phase 7)

- PaymentService owns payment-attempt orchestration while OrderService remains
  the owner of order confirmation. Provider calls use a stable idempotency key
  and are modeled as external side effects; database transactions cannot roll
  back a remote charge, so persisted state, lookup and compensation support
  recovery.
- The sandbox PaymentProvider is deterministic and never receives or stores
  PAN/CVV, bank credentials or wallet passwords. Payment and refund attempts
  have strict state machines and append-only event history.
- Payment locking serializes refund accounting. Completed refunds alone reduce
  the refundable balance; uncertain provider outcomes are marked for recovery
  rather than reported as successful.

## Reviews, notifications, admin, audit and reports (Phase 8)

- Reviews are verified-purchase-only and strict: a CUSTOMER may review a
  product only after the order containing it has reached DELIVERED
  (PENDING/CONFIRMED/PROCESSING/SHIPPED never qualify). One review per
  customer/product is enforced by the database; rating 1-5 and the
  PENDING -> APPROVED / REJECTED moderation states are constrained in the
  schema. New reviews and every edit start PENDING; STAFF and ADMIN approve or
  reject, and only APPROVED reviews appear on public pages and count toward
  rating summaries. Every moderation decision and review deletion is audit
  logged (REVIEW_APPROVED / REVIEW_REJECTED / REVIEW_DELETED).
- The notification module is a customer notification center fed in-process
  from domain events and service call sites: order lifecycle events
  (`OrderStatusChangedEvent` published by `OrderService` and
  `PaidCancellationService`), `RefundCompletedEvent` in the payment module,
  plus WELCOME (email verification), PASSWORD_CHANGED / PASSWORD_RESET
  (auth module) and PAYMENT_SUCCEEDED / definitive PAYMENT_FAILED (payment
  module). Synchronous `@EventListener`/direct handlers persist notifications
  inside the publisher's transaction, so retries and idempotent replays never
  duplicate them. Kafka/Redis delivery is deferred to Phase 9.
- The `alert` module holds transition-based, deduplicated operational alerts
  for administrators: LOW_STOCK_ALERT fires only when a durable inventory
  mutation crosses the low-stock threshold (never on reads, deduplicated per
  crossing) and FAILED_PAYMENT_ALERT fires once per definitive payment failure
  (transient retries inside the retry loop never produce it).
  `/api/v1/admin/alerts` lists and acknowledges them.
- ADMIN account administration lives in the `user` module under
  `/api/v1/admin/users` (list/detail/activate/deactivate/lock/unlock/role).
  Locking, deactivating or changing a role revokes the target's refresh
  sessions; self lock/deactivate/demotion is refused (password change/reset
  already revoked sessions). Actions are audited as USER_ACTIVATED /
  USER_DEACTIVATED / USER_LOCKED / USER_UNLOCKED / USER_ROLE_CHANGED.
- The `audit` module stores append-only `audit_logs` entries for every
  administrative and sensitive action across modules: account
  (USER_*), catalog (PRODUCT_* incl. PRODUCT_PRICE_UPDATED), category
  (CATEGORY_*), variant (VARIANT_*), inventory (STOCK_RESTOCKED /
  STOCK_ADJUSTED / SALE_REVERSED), promotion (PROMOTION_*), order
  (ORDER_STATUS_CHANGED / ORDER_CANCELLED / PAID_ORDER_CANCELLED), payment
  (PAYMENT_RECONCILED / REFUND_CREATED / REFUND_COMPLETED) and review
  moderation. There is no update/delete API and details never contain
  credentials/tokens; administrators search the log at
  `/api/v1/admin/audit-logs`.
- `GET /api/v1/admin/dashboard` returns repository-backed metrics: total and
  net revenue (BigDecimal, captured orders only minus completed refunds),
  order counts by status, customer/active-product/low-stock/failed-payment
  counts and recent orders.
- The `report` module exposes ADMIN-only read-only reports for sales,
  top-products, reviews, orders, customers, inventory, payments and refunds
  with date ranges, status/role filters and pagination. The same reports are
  exported as RFC-4180 CSV (`/sales.csv`, `/orders.csv`, ...) with UTF-8,
  Content-Disposition, deterministic columns, proper escaping and spreadsheet
  formula-injection neutralization of cells starting with `= + - @`. Aggregate
  queries are declared on the repositories that own the source tables.
- `SecurityConfig`: `POST /api/v1/products/*/reviews` is CUSTOMER-only;
  review moderation (list + approve/reject) allows STAFF and ADMIN;
  `/api/v1/admin/**` (users, audit-logs, alerts, dashboard, reports) stays
  ADMIN-only; `/api/v1/staff/**` stays STAFF+ADMIN.

## Distributed integration and observability (Phase 9)

- CommerceCore remains the transaction-owning modular monolith for accounts,
  catalog, inventory, checkout, orders, payments, refunds, reviews, alerts,
  audit and reports. Only the low-coupling notification capability is
  extracted. The runtime boundary is API Gateway -> Core Application plus
  Notification Service; orders, payments and inventory are not split across
  services.
- Core transactions atomically persist business state and a versioned JSON
  `EventEnvelope` in the PostgreSQL `outbox_events` table (Flyway V13). A
  bounded `FOR UPDATE SKIP LOCKED` publisher sends confirmed records to the
  appropriate Kafka topic using stable aggregate keys and marks them
  published only after broker acknowledgement. Failed publication is retried
  with backoff; only old published rows are eligible for configurable cleanup.
- Kafka topics are grouped by domain and version:
  `commercecore.user.events.v1`, `commercecore.order.events.v1`,
  `commercecore.payment.events.v1`, `commercecore.inventory.events.v1` and
  `commercecore.review.events.v1`. Delivery is at least once. Notification
  consumers make effects idempotent with a durable `processed_events` primary
  key in the independently owned notification PostgreSQL database. Bounded
  consumer retries end in `commercecore.notification.events.dlq`; malformed
  envelopes and unsupported versions cannot mutate notification state.
- The Notification Service consumes supported lifecycle events and owns all
  customer notification persistence. Its database is created and migrated by
  the service's own Flyway V1 history, and it never queries Core tables.
  Notification REST endpoints retain their existing public paths and validate
  the same JWT configuration for defense in depth and customer isolation.
- Redis is a non-authoritative cache-aside layer for product details and rating
  summaries. Keys are namespaced/versioned and have bounded configurable TTLs.
  Successful catalog/review writes invalidate affected entries after commit.
  Redis errors are metered and logged while reads fall back to PostgreSQL;
  inventory, checkout, payment, refund and authorization correctness remain
  database-owned.
- The lightweight gateway preserves public API paths, routes notification APIs
  to the extracted service and all other APIs to Core, strips client-supplied
  internal identity headers, and propagates a validated or generated
  `X-Correlation-Id`. Correlation is retained through Core logs, outbox
  envelopes, Kafka and notification processing.
- All three applications expose safe Actuator health, liveness, readiness,
  metrics and Prometheus endpoints. Low-cardinality Micrometer counters and
  gauges cover outbox publication, consumer processing/duplicates/retries/DLQ,
  notification creation and Redis cache results. Local Docker Compose adds
  PostgreSQL databases, Kafka in KRaft mode, Redis and Prometheus without
  exposing sensitive configuration endpoints.
