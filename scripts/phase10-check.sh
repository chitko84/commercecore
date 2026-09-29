#!/usr/bin/env sh
set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

required_files="
$root_dir/frontend/package.json
$root_dir/frontend/playwright.config.ts
$root_dir/frontend/nginx.conf
$root_dir/frontend/e2e/phase10.spec.ts
$root_dir/.github/workflows/ci.yml
$root_dir/docker-compose.yml
$root_dir/infra/terraform/versions.tf
"

for file in $required_files; do
  if [ ! -f "$file" ]; then
    echo "Missing required Phase 10 artifact: $file" >&2
    exit 1
  fi
done

cd "$root_dir/frontend"
npm run lint
npm run typecheck
npm test -- --run
npm run build

cd "$root_dir"
./mvnw --batch-mode test

DB_PASSWORD=${DB_PASSWORD:-phase10_check_only} \
NOTIFICATION_DB_PASSWORD=${NOTIFICATION_DB_PASSWORD:-phase10_notification_check_only} \
JWT_SIGNING_SECRET=${JWT_SIGNING_SECRET:-phase10_check_secret_at_least_32_bytes_long} \
  docker compose config --quiet

if [ "${RUN_E2E:-false}" = "true" ]; then
  cd "$root_dir/frontend"
  npm run test:e2e
fi

echo "Phase 10 validation passed."
