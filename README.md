# CommerceCore

CommerceCore is a production-oriented full-stack commerce application. It includes a public storefront, customer account/cart/checkout/order workflows, staff operations, admin reporting and audit tools, asynchronous notifications, and observable local infrastructure.

The ten-phase development plan is complete. The transactional commerce domains remain in the Spring Boot modular monolith; Phase 9 extracted only the Notification Service and placed both services behind the API Gateway.

## Architecture

```text
Browser
  -> Nginx + React SPA
       -> /api/* -> API Gateway
                       -> CommerceCore core -> PostgreSQL
                       -> Notification Service -> notification PostgreSQL
                  core -> transactional outbox -> Kafka -> Notification Service
                  core -> Redis (non-authoritative catalog cache)
```

- Frontend: React 18, TypeScript 5.6, Vite 6, React Router, TanStack Query, React Hook Form, Zod
- Backend: Java 21, Spring Boot 3.5.16, Spring Security/JWT, JPA, Flyway, Maven Wrapper
- Data and messaging: PostgreSQL 16, Redis 7.4, Kafka 3.9 KRaft
- Tests: JUnit 5, Testcontainers, Vitest, Testing Library, Playwright
- Operations: Docker Compose, Nginx, Prometheus, GitHub Actions, Terraform/AWS reference deployment

See [ARCHITECTURE.md](ARCHITECTURE.md), [DECISIONS.md](DECISIONS.md), and [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for deeper design and deployment detail.

## Repository structure

```text
frontend/                 React SPA, unit tests, Playwright tests, Nginx image
src/main/java/            Core, API Gateway, and Notification Service code
src/main/resources/       Spring profiles and Flyway migrations
src/test/                 Backend unit/integration tests
observability/            Prometheus configuration
infra/terraform/          AWS ECS/RDS/MSK/Redis deployment reference
scripts/                  Stack wait and final Phase 10 validation scripts
.github/workflows/        CI pipeline
docker-compose.yml        Complete local production-like stack
```

## Prerequisites

- Docker Desktop or Docker Engine with Compose v2
- Java 21 for native backend development
- Node.js 22 and npm for native frontend development
- Optional: Terraform 1.8–1.x for infrastructure work (CI and the documented container command pin 1.10.5)

## Environment setup

1. Copy `.env.example` to `.env`.
2. Replace `DB_PASSWORD`, `NOTIFICATION_DB_PASSWORD`, and `JWT_SIGNING_SECRET`. The JWT secret must be at least 32 bytes.
3. Leave `APP_DEMO_SEED=false` except for local demos/E2E.
4. For standalone frontend development, optionally copy `frontend/.env.example` to `frontend/.env.local`.

The browser defaults to the same-origin `/api/v1` path. `VITE_API_BASE_URL` may override it, and `VITE_API_TIMEOUT_MS` controls the browser request timeout. Never place credentials in a `VITE_*` variable; those values are public in the client bundle.

All supported backend variables and safe local examples are documented in `.env.example`. Production additionally requires real SMTP settings. `.env` files are ignored; do not commit secrets.

## Run the complete local stack

The recommended clean startup path is:

```bash
docker compose up -d --build --wait
```

Open:

- Application: `http://localhost:3000`
- Gateway health: `http://localhost:8080/actuator/health`
- Prometheus: `http://localhost:9090`

If port 8080 is occupied, set `GATEWAY_PORT=18080`; the frontend still reaches the gateway over the internal Compose network.

Flyway automatically applies core migrations V1–V13 and the Notification Service migration when the services start. Hibernate validates the resulting schema and never updates it.

Stop the stack with `docker compose down`. Use `docker compose down -v` only when you intentionally want to delete local databases and start from empty schemas.

## Demo data and accounts

Set `APP_DEMO_SEED=true` only in a local/demo environment. The idempotent seeder creates catalog, inventory, promotions, an address, and a delivered paid order for review testing.

| Role | Email | Password |
|---|---|---|
| Customer | `demo@commercecore.dev` | `DemoPass!2026` |
| Staff | `staff@commercecore.dev` | `StaffPass!2026` |
| Admin | `admin@commercecore.dev` | `AdminPass!2026` |

These are intentionally public demo credentials and must never be enabled in production.

## Native development

Start only local dependencies when running the core from the IDE:

```bash
docker compose up -d postgres notification-postgres kafka redis
./mvnw spring-boot:run
```

On Windows use `mvnw.cmd`. Load the values from `.env` into the Spring process environment; Spring itself does not read `.env` files.

Run the frontend development server in another terminal:

```bash
cd frontend
npm ci
npm run dev
```

`VITE_DEV_API_TARGET` defaults to `http://localhost:8080`, so run the gateway there or override the target.

## Tests and builds

Backend regression (Docker is required for all Testcontainers tests):

```bash
./mvnw test
```

Frontend quality gates:

```bash
cd frontend
npm ci
npm test
npm run typecheck
npm run lint
npm run build
```

Playwright system tests require the complete stack with demo seed data:

```bash
cd frontend
npx playwright install chromium
npm run test:e2e
```

The E2E suite verifies storefront loading, required responsive viewports, successful and failed login, protected routes, customer/admin RBAC, real cart/promotion/checkout/payment/order/notification behavior, review moderation, admin operations, and logout.

The final local gate combines the static/unit/backend/Compose checks. Set `RUN_E2E=true` when the seeded stack is already running:

```bash
sh scripts/phase10-check.sh
RUN_E2E=true sh scripts/phase10-check.sh
```

## Production images and routing

Both Dockerfiles are multi-stage builds. The frontend image serves static assets through Nginx, falls back to `index.html` for deep SPA routes, proxies `/api/*` to the gateway, exposes `/healthz`, and sends practical browser security headers.

Validate and build the local deployment with:

```bash
docker compose config --quiet
docker compose build
```

The Compose services include readiness/health checks and dependency ordering for PostgreSQL, Kafka, Redis, core, Notification Service, gateway, Prometheus, and frontend.

## AWS Terraform deployment

`infra/terraform` is the maintainable AWS reference: private ECS/Fargate services, separate RDS PostgreSQL databases, MSK, encrypted Redis, ECR, CloudWatch, service discovery, Secrets Manager, an HTTPS ALB, and Route 53. It does not create or apply paid infrastructure automatically.

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform fmt -check -recursive
terraform validate
terraform plan
```

Before any apply, provide immutable image URIs, a validated ACM certificate, hosted-zone values, SMTP configuration, and populate the runtime Secrets Manager JSON keys documented in [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md). Review cost, backups, deletion protection, DNS, and regional capacity with the deployment owner.

## CI

`.github/workflows/ci.yml` runs backend tests, frontend lint/typecheck/tests/build, Terraform format/init/validate, production Docker builds, a clean Compose stack, and Playwright E2E. Ordinary CI uses only disposable local values and needs no committed secrets.

## Security notes

- Bearer JWT authorization is enforced by core and Notification Service; the UI is not the security boundary.
- Customer, staff, and admin routes are separately authorized server-side.
- Refresh tokens rotate, passwords use delegating BCrypt, and sensitive token values are not logged.
- Production CORS is environment-controlled and production Swagger/OpenAPI is disabled.
- Nginx adds CSP, frame, content-type, referrer, and permissions headers.
- The AWS reference terminates TLS at the ALB and retrieves runtime secrets from Secrets Manager.
- Payment processing is a simulated sandbox provider; no raw financial credentials are accepted or stored.

This is practical hardening, not a claim of formal security certification. Rotate secrets, patch dependencies, restrict cloud IAM/network access, and run organization-specific security review before a real launch.

## Troubleshooting

- Tests reported as skipped: start Docker and rerun `./mvnw test`; a final regression must show zero skipped integration tests.
- `npm.ps1` is blocked on Windows: use `npm.cmd` or configure an appropriate PowerShell execution policy.
- Port conflict: override the matching variable in `.env` (`GATEWAY_PORT`, `FRONTEND_PORT`, database/broker ports).
- Frontend shows a network error: check `docker compose ps`, gateway/core health, and `docker compose logs api-gateway commercecore-core`.
- Deep-link refresh returns 404: use the Nginx frontend image; its SPA fallback is already configured.
- Demo accounts missing: set `APP_DEMO_SEED=true` before creating the clean local stack.
- Stale demo state: intentionally run `docker compose down -v`, then rebuild with demo seeding enabled.

Public API contracts are available through Swagger only in non-production profiles at `/swagger-ui.html` and `/v3/api-docs` on the core service.
