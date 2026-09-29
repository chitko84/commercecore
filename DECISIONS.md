# DECISIONS.md

Concise ADR-style log. Add new entries at the bottom; don't rewrite
history.

---

### ADR-001: Modular monolith before microservices
**Status:** Accepted
**Decision:** Build CommerceCore as a single modular-monolith Spring
Boot application with clear package boundaries per business domain.
Microservices are deferred to Phase 9.
**Why:** Avoids premature distributed-systems complexity while still
keeping module boundaries clean enough to extract services later.

---

### ADR-002: PostgreSQL as the primary database
**Status:** Accepted
**Decision:** Use PostgreSQL for all relational data.
**Why:** Mature, widely supported, strong fit for transactional
e-commerce workloads (inventory, orders, payments).

---

### ADR-003: Flyway for schema migrations
**Status:** Accepted
**Decision:** All schema changes are versioned Flyway migrations. No
Hibernate auto-DDL in shared/deployed environments.
**Why:** Reproducible, auditable schema history across environments.

---

### ADR-004: Java 21
**Status:** Accepted
**Decision:** Target Java 21 (LTS) throughout the project.
**Why:** Current LTS with modern language features, good long-term
support window, aligns with Spring Boot 3.x baseline.

---

### ADR-005: API entities separated from DTOs
**Status:** Accepted
**Decision:** JPA entities never cross the controller boundary. All API
request/response payloads are dedicated DTOs, mapped in the service
layer.
**Why:** Prevents leaking persistence details/lazy-loading issues into
the API contract and keeps API evolution decoupled from schema
evolution.

---

### ADR-006: Centralized API error model via @RestControllerAdvice
**Status:** Accepted
**Decision:** All API errors (validation, malformed request, not-found,
unexpected server error) go through a single `GlobalExceptionHandler`
and return a consistent `ApiError` JSON shape. Stack traces are never
returned to clients.
**Why:** Predictable, client-safe error contract from the start,
before any business endpoints exist to accumulate ad hoc error
handling.

---

### ADR-007: Temporary Phase-1 security config, not real auth
**Status:** Superseded by ADR-011 in Phase 2
**Decision:** A minimal `SecurityFilterChain` permits only
`/actuator/health` and Swagger/OpenAPI paths without authentication;
everything else requires authentication. No JWT/login/registration/RBAC
yet.
**Why:** Infrastructure endpoints (health checks, API docs) need to be
reachable during Phase 1 development/verification without pulling
Phase 2's authentication system in early.

---

### ADR-008: Environment-variable-driven profiles, no secrets committed
**Status:** Accepted
**Decision:** `dev`/`test`/`prod` Spring profiles; `dev` has safe local
defaults for non-secret settings but requires `DB_PASSWORD`, while `prod`
has no defaults (fails fast without env vars). Real
credentials only ever live in a git-ignored `.env`; `.env.example`
documents variable names with placeholder values.
**Why:** Keeps the repo safe to make public (portfolio project) while
still being easy to run locally.

---

### ADR-009: Single DB_* variable set for Compose + Spring datasource
**Status:** Accepted
**Decision:** `docker-compose.yml` reads `DB_NAME`/`DB_USERNAME`/
`DB_PASSWORD`/`DB_PORT` (the same variables `application-dev.yml`
reads) rather than a separate, duplicate `POSTGRES_*` variable set.
`DB_PASSWORD` has no default anywhere and Compose fails fast with a
clear message if it's unset.
**Why:** S001B had two variable sets (`POSTGRES_*` for Compose,
`DB_*` for Spring) that could silently drift apart. One set removes
that drift risk and matches the corrected README, which now states
plainly that `.env` is required before `docker compose up`.

---

### ADR-010: Canonical lowercase user email without a database extension
**Status:** Accepted
**Decision:** `UserAccount` normalizes email with trim + `Locale.ROOT`
lowercasing. PostgreSQL V2 requires canonical lowercase storage and applies a
unique constraint to `users.email`; no `citext` extension is introduced.
**Why:** Email is the login identifier. Enforcing the same canonical form in
the domain and schema prevents case variants from becoming separate accounts
while keeping the database portable and the indexed lookup simple.

---

### ADR-011: Spring Security JWT with opaque rotating refresh sessions
**Status:** Accepted
**Decision:** Sign 15-minute HS256 access JWTs with an environment-supplied
secret and Spring Security JOSE. Refresh tokens are opaque random values whose
SHA-256 hashes are stored in database-backed families; rotation revokes the old
token and reuse revokes the active family.
**Why:** Keeps access validation stateless while supporting durable logout,
rotation, reuse detection, and session revocation without persisting raw tokens.

---

### ADR-012: Delegating BCrypt password encoding and hash-only action tokens
**Status:** Accepted
**Decision:** Passwords use Spring Security's delegating BCrypt encoder.
Verification and password-reset tokens are secure random values with only
SHA-256 hashes stored, plus expiry and single-use state.
**Why:** Uses maintained Spring primitives, permits future password-hash
upgrades, and limits damage from a database disclosure.

---

### ADR-013: Configurable temporary login lock
**Status:** Accepted
**Decision:** Five failed logins temporarily lock an active account for 20
minutes by default. Threshold and duration are configuration values; a locked
account with no expiry represents a manual lock.
**Why:** Provides practical brute-force resistance without conflating temporary
protection, administrator locks, or deactivation.

---

### ADR-014: Adjacency-list categories with soft deactivation
**Status:** Accepted
**Decision:** Represent category hierarchy with a nullable parent foreign key.
Reject self-parenting/cycles in the service, cascade deactivation through
descendants, and retain categories/products rather than expose destructive
catalog deletion.
**Why:** The catalog needs ordinary tree traversal and durable history, not the
write complexity of nested sets or materialized paths.

---

### ADR-015: Canonical catalog identifiers and PostgreSQL/JPA search
**Status:** Accepted
**Decision:** Store slugs in lowercase and SKUs in uppercase canonical form,
with database checks and uniqueness. Implement current catalog search with
composable Spring Data Specifications, a fixed sort vocabulary and PostgreSQL
indexes; use sale price as effective price when present.
**Why:** This gives reliable identifiers and extensible V1 filtering without an
external search service or unsafe client-selected persistence properties.

---

### ADR-016: Product media is ordered metadata
**Status:** Accepted
**Decision:** Persist media URL/path, alt text, display order and primary state;
do not store or upload binary assets. Enforce one primary item per product with
a PostgreSQL partial unique index.
**Why:** Catalog APIs need stable media presentation now, while storage and
asset-delivery infrastructure can evolve independently later.

---

### ADR-017: Generic variant attributes and owner-XOR inventory
**Status:** Accepted
**Decision:** Store variant attributes as ordered name/value rows unique by
case-insensitive name per variant. Each inventory row references exactly one
product or variant, enforced by XOR and partial unique indexes; products with
variants use variant inventory.
**Why:** It supports arbitrary sellable options without a premature PIM while
keeping stock ownership unambiguous.

---

### ADR-018: Single inventory accounting formula and derived status
**Status:** Accepted
**Decision:** Use `available = total - reserved - sold` everywhere. Never
persist available quantity or stock status; derive UNKNOWN, OUT_OF_STOCK,
LOW_STOCK or IN_STOCK from the owner record and threshold.
**Why:** One constrained source of truth prevents drift between counters and
public availability.

---

### ADR-019: Pessimistic inventory locking and idempotent reservations
**Status:** Accepted
**Decision:** Every stock mutation loads its inventory row with
`PESSIMISTIC_WRITE` inside the transaction. Reservation references are unique
per inventory; matching ACTIVE/CONFIRMED retries return the original result,
while other repeat transitions fail.
**Why:** Database-row serialization prevents overselling across processes, and
reference idempotency makes later checkout retries safe.

---

### ADR-020: Append-only inventory movement history
**Status:** Accepted
**Decision:** Record every restock, adjustment, reserve, release, sale and
expiry as a new immutable movement with before/after counters and compact
metadata. Expose no update/delete history API.
**Why:** This gives an auditable stock trail without mutable snapshots or order
domain coupling.

---

### ADR-021: Persistent carts inspect but do not reserve inventory
**Status:** Accepted
**Decision:** Keep one lazily created cart per customer. Cart lines use current
catalog prices and inspect live availability, but cart and wishlist operations
never reserve or sell inventory; Phase 6 owns reservation timing and order price
snapshots.
**Why:** A cart expresses intent, not a committed purchase. Reserving during
browsing would strand stock and blur the checkout transaction boundary.

---

### ADR-022: One deterministic coupon per cart
**Status:** Accepted
**Decision:** V1 supports one applied coupon without stacking. Monetary results
use `BigDecimal`, scale 2, and `HALF_UP`; discounts are capped at eligible
subtotal. Product scope targets one product, while category scope includes all
descendants in the existing adjacency-list hierarchy.
**Why:** A single documented evaluation order avoids ambiguous stacking while
providing useful cart-, product-, and category-scoped promotions.

---

### ADR-023: Coupon application is not redemption
**Status:** Accepted
**Decision:** Applying a coupon stores only the cart association and revalidates
it on every read. Durable usage is recorded only by an internal pessimistically
locked, reference-idempotent redemption operation for Phase-6 checkout. A
free-shipping promotion exposes eligibility, never an invented shipping amount.
**Why:** Usage limits must reflect committed purchases, while shipping cost and
the order commit point do not exist until Phase 6.

---

### ADR-024: Checkout idempotency and snapshots
**Status:** Accepted  
**Decision:** Require a customer-scoped `Idempotency-Key` and deterministic
request fingerprint. Checkout creates PENDING orders and snapshots mutable
catalog, address, promotion, shipping and price data transactionally.  
**Why:** Retries must not duplicate orders or reservations, and historical
orders must remain stable after later edits.

### ADR-025: Reservation and promotion commit boundaries
**Status:** Accepted  
**Decision:** Checkout reserves inventory through the existing pessimistic-lock
service. Pending orders expire after a configurable TTL. Only internal
`confirmAfterPayment` confirms reservations and records promotion redemption;
pending cancellation releases stock.  
**Why:** Payment/refund behavior belongs to Phase 7 while stock safety belongs
inside the checkout transaction.

### ADR-026: V1 shipping, tax, and tracking
**Status:** Accepted  
**Decision:** Use configurable STANDARD/EXPRESS shipping, free-shipping
discounts, tax as a percentage of discounted merchandise, and one shipment per
order with append-only tracking events and strict transitions.  
**Why:** This provides deterministic checkout foundations without external
carrier or tax-provider integrations.

---

### ADR-027: Provider abstraction and persisted payment intent
**Status:** Accepted  
**Decision:** PaymentService depends on a provider interface; V1 uses a
deterministic sandbox. Provider idempotency keys are durable and remote calls
are never assumed to participate in the database transaction.  
**Why:** Provider success, timeout and local commit failure require safe lookup
and recovery without storing financial credentials.### ADR-028: Payment reliability and refunds
**Status:** Accepted
**Decision:** Classify declines, transient and uncertain outcomes; retain
truthful local states and mark reconciliation when needed. Refunds are
customer/order scoped and pessimistically locked, with completed-refund totals
preventing over-refund and idempotent retries.
**Why:** Bounded retries and durable state avoid duplicate charges/refunds while
leaving operational recovery explicit for later provider integrations.

---

### ADR-029: Verified-purchase reviews with DELIVERED-only eligibility and PENDING->APPROVED/REJECTED moderation
**Status:** Accepted (amended by the Phase 8 gap audit)
**Decision:** A review is created only by a customer who owns a DELIVERED order
item for the product; PENDING/CONFIRMED/PROCESSING/SHIPPED never qualify, so
the review reflects an actually received item. A unique (customer, product)
constraint allows at most one review. Every new review and every edit starts
PENDING; STAFF and ADMIN approve or reject, and only APPROVED reviews are
public and included in rating summaries. Reviews are retained on deactivation
and only hard-deleted by their author.
**Why:** The original spec demanded delivered-goods-only reviews and a
PENDING/APPROVED/REJECTED moderation workflow with STAFF participation; the
audit restored that contract (the interim VISIBLE/HIDDEN, ADMIN-only,
paid-item variant was narrowed and is superseded).

---

### ADR-030: In-app notifications from in-process domain events and service call sites
**Status:** Accepted (amended by the Phase 8 gap audit)
**Decision:** Notifications are persisted synchronously inside the publisher's
transaction from the full required event vocabulary: WELCOME and account
activation, PASSWORD_CHANGED, PASSWORD_RESET, ORDER_CREATED/CONFIRMED/
PROCESSING/SHIPPED/DELIVERED/CANCELLED, PAYMENT_SUCCEEDED, definitive
PAYMENT_FAILED and REFUND_COMPLETED. Order/payment publishers are idempotent
and handlers are synchronous `@EventListener`/direct calls, so retries and
idempotent replays never duplicate notifications.
**Why:** A customer notification center needs the complete lifecycle vocabulary
now, but Kafka/Redis are deferred to Phase 9. Synchronous, in-transaction
handlers give exactly-once-per-transition notifications without a broker and
keep order/payment modules decoupled from notification internals.

---

### ADR-031: Unified append-only audit log for admin/sensitive actions
**Status:** Accepted (amended by the Phase 8 gap audit)
**Decision:** The audit module stores an append-only `audit_logs` entry for
every administrative and sensitive action across modules: account
(USER_ACTIVATED/DEACTIVATED/LOCKED/UNLOCKED/ROLE_CHANGED), catalog
(PRODUCT_* including price, CATEGORY_*, VARIANT_*), inventory
(STOCK_RESTOCKED/STOCK_ADJUSTED/SALE_REVERSED), promotion (PROMOTION_*),
order (ORDER_STATUS_CHANGED/ORDER_CANCELLED/PAID_ORDER_CANCELLED), payment
(PAYMENT_RECONCILED/REFUND_CREATED/REFUND_COMPLETED) and review moderation
(REVIEW_APPROVED/REVIEW_REJECTED/REVIEW_DELETED). There is no update/delete
API; the log is immutable and details never contain passwords, hashes, JWTs,
refresh tokens, reset/verification tokens, payment tokens or secrets.
Administrators query the log under `/api/v1/admin/audit-logs`.
**Why:** Phase-8 administrative surfaces need accountability; one append-only
log (alongside the existing domain event/history tables) gives a single
searchable record of who did what across all staff/admin surfaces.

---

### ADR-032: Operational admin alerts are transition-based and deduplicated
**Status:** Accepted
**Decision:** The `alert` module records LOW_STOCK_ALERT and
FAILED_PAYMENT_ALERT rows for administrators. LOW_STOCK_ALERT fires only when
a durable inventory mutation crosses the low-stock threshold (reads never
alert; each threshold crossing is deduplicated). FAILED_PAYMENT_ALERT fires
once per definitive payment failure; transient retries inside the payment
retry loop never produce it. Alerts are listable and acknowledgeable at
`/api/v1/admin/alerts` and are audit-logged when acknowledged.
**Why:** Operational alerts must reflect state transitions, not reads or
fleeting retry attempts, otherwise administrators get noise and false
failed-payment alarms.

---

### ADR-033: Repository-backed admin dashboard
**Status:** Accepted
**Decision:** `GET /api/v1/admin/dashboard` computes metrics from owner-module
repository aggregates: total and net revenue in BigDecimal from captured
orders minus completed refunds (pending/failed/cancelled payments excluded),
order counts by status, customer count, active-product count, low-stock count,
failed-payment count and recent orders.
**Why:** A dashboard must be cheap, read-only and truthful; computing from the
same aggregates that reports use keeps it consistent with the reports module.

---

### ADR-034: Full report set and safe CSV export
**Status:** Accepted
**Decision:** The report module exposes ADMIN-only read-only reports for sales,
orders, customers, inventory, payments, refunds, top products and reviews with
date ranges, status/role filters and pagination. Each report also exports as
UTF-8 CSV (`/sales.csv`, `/orders.csv`, ...) with `text/csv`,
Content-Disposition, deterministic columns, RFC-4180 quoting/escaping, and
cells starting with `= + - @` neutralized (prefixed) to prevent spreadsheet
formula injection. Aggregate queries live on the repositories that own the
source tables; `ReportService` only composes them and never mutates data.
**Why:** The original Phase 8 spec required six core reports plus CSV with
explicit escaping and formula-injection defense; the interim three-report set
was narrowed and is superseded.

---

### ADR-035: Admin session revocation on privilege state changes
**Status:** Accepted
**Decision:** Locking, deactivating, or changing a user's role revokes that
user's refresh-token family, forcing re-login; password change/reset already
revoked sessions. Self lock/deactivate/demotion is refused so an ADMIN cannot
lock themselves out or shed their own privileges.
**Why:** Security state changes must take effect immediately rather than on
token expiry, and self-modification guards keep administrative access from
being accidentally destroyed.

---

### ADR-036: Extract only the Notification Service
**Status:** Accepted
**Decision:** Phase 9 keeps CommerceCore as the main modular monolith and
extracts only customer notification processing and persistence. Orders,
payments and inventory remain in Core. Notification Service owns a separate
PostgreSQL database and does not read Core tables.
**Why:** Notifications are naturally asynchronous and tolerate temporary
unavailability; splitting transaction-sensitive commerce modules would add
distributed-transaction risk without Phase-9 value.

---

### ADR-037: Transactional outbox with at-least-once delivery
**Status:** Accepted
**Decision:** A Core business transaction writes its versioned event envelope
to `outbox_events` in the same PostgreSQL commit. A bounded, multi-instance-safe
publisher uses row locking with `SKIP LOCKED`, Kafka `acks=all` and idempotent
production, then records broker-confirmed publication. Transport delivery is
explicitly at least once, not exactly once. Notification Service atomically
persists the notification and durable processed-event marker, so duplicate
delivery creates no duplicate business effect. Published history is retained
for a configurable period; unpublished and unresolved rows are never cleaned.
**Why:** This closes the database/Kafka dual-write gap while acknowledging and
controlling the duplicate-delivery behavior of recoverable distributed flows.

---

### ADR-038: Explicit event contracts, topic grouping and dead letters
**Status:** Accepted
**Decision:** Public Kafka messages use JSON `EventEnvelope` contracts with a
UUID event ID, semantic event type, explicit integer version, occurrence time,
correlation ID, aggregate identity and payload. Additive fields are compatible
and consumers ignore unknown JSON fields; semantic breaking changes require a
new version. Domain-version topics use stable aggregate keys. Malformed,
semantically invalid and unsupported-version messages use bounded retry where
appropriate and ultimately retain diagnostic metadata in
`commercecore.notification.events.dlq`.
**Why:** Explicit contracts and finite failure handling let producers and
consumers evolve independently without poison-message retry loops or relying
on Java class names/serialization.

---

### ADR-039: Redis is a fail-open, non-authoritative catalog cache
**Status:** Accepted
**Decision:** Redis provides cache-aside product-detail and rating-summary
reads using `commercecore:v1:*` namespaced keys and bounded configurable TTLs.
Core writes invalidate affected entries only after PostgreSQL commits. Redis
failure logs/meters an error and falls back to PostgreSQL. Redis never owns
inventory availability, checkout, payment/refund state, credentials, tokens or
authorization decisions.
**Why:** Catalog reads benefit from caching, while database truth and existing
locking/idempotency rules remain available and correct during a cache outage.

---

### ADR-040: Gateway trust boundary and correlation propagation
**Status:** Accepted
**Decision:** The API Gateway is the single public Phase-9 route, removes
untrusted internal identity headers, adds or validates `X-Correlation-Id`, and
contains no business logic. Core and Notification Service still validate the
shared JWT signing configuration, so direct reachability does not turn a
forwarded user ID into an authorization boundary. The correlation ID is copied
to logs, outbox envelopes, Kafka messages and notification processing.
**Why:** Routing and cross-service diagnostics belong at the edge, while
defense-in-depth authorization prevents clients from forging service identity.

---

### ADR-041: Low-cardinality operational metrics
**Status:** Accepted
**Decision:** Actuator and Prometheus expose health/probes plus custom metrics
for outbox state/results, Kafka consumption/duplicates/retries/DLQ,
notification creation and Redis cache hit/miss/error. Metric tags may describe
bounded categories such as service or result, but never user, order, payment,
aggregate or event identifiers. Logs carry diagnostic identifiers instead and
must not contain tokens, credentials or full sensitive payloads.
**Why:** Bounded labels keep metrics operationally useful without unbounded
time-series growth or sensitive/high-cardinality leakage.
