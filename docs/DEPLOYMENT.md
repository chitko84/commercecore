# Deployment

## Local production-like deployment

Copy `.env.example` to `.env`, replace all local secrets, and run:

```bash
docker compose up -d --build --wait
```

Traffic enters through the frontend Nginx container on port 3000. Nginx serves the SPA and proxies `/api/*` to the API Gateway. The gateway routes notification reads to Notification Service and other API traffic to the core. Health checks prevent dependents from starting before required services are ready.

For deterministic E2E validation, use a disposable stack with `APP_DEMO_SEED=true`. Never enable the demo seeder in production.

## AWS reference deployment

The Terraform configuration under `infra/terraform` is an explicit reference for the existing architecture; it does not run from CI and is never automatically applied.

Prerequisites:

- AWS credentials supplied outside the repository
- immutable core, notification, gateway, and frontend images in ECR
- a Route 53 hosted zone and public hostname
- a validated ACM certificate in the deployment region
- a production SMTP account and verified From address

Copy `terraform.tfvars.example` to the ignored `terraform.tfvars`, replace every example, then run `terraform init`, `terraform validate`, and `terraform plan`. Review the plan and expected AWS cost before applying.

Terraform creates the Secrets Manager container but not secret values. Before starting ECS services, store one JSON object at the output `runtime_secret_arn` with these keys:

```json
{
  "core_db_password": "generated-value",
  "notification_db_password": "generated-value",
  "jwt_signing_secret": "generated-value-at-least-32-bytes",
  "smtp_password": "provider-value"
}
```

Do not place those values in `.tfvars`, Terraform state, task environment arrays, CI variables, or source control.

The ALB redirects HTTP to HTTPS and routes `/api/*` directly to the gateway; all other paths go to the frontend. ECS tasks stay in private subnets, RDS and Redis are not public, data stores use encryption, and the application containers publish health checks to ECS.

## Release sequence

1. Run backend and frontend quality gates.
2. Build and scan all four images.
3. Tag images immutably (for example, with the commit SHA) and push to ECR.
4. Update `container_images` in the deployment input.
5. Run `terraform fmt -check`, `terraform validate`, and `terraform plan`.
6. Apply only after an owner reviews cost, security groups, backups, DNS, certificate, and deletion protection.
7. Verify `/healthz`, gateway readiness, authentication/RBAC, checkout, notifications, and a deep SPA route.
8. Monitor CloudWatch, Prometheus metrics, the outbox backlog, Kafka consumer lag, and DLQ volume.

Database migrations run on application startup. Back up both databases and rehearse restore/rollback procedures before a production release; never edit an already-applied Flyway migration.
