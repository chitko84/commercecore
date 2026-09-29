#!/usr/bin/env sh
set -eu
url="${1:-http://localhost:3000/healthz}"
timeout="${2:-300}"
elapsed=0
until curl --fail --silent --show-error "$url" >/dev/null 2>&1; do
  if [ "$elapsed" -ge "$timeout" ]; then
    echo "Timed out waiting for $url after ${timeout}s" >&2
    docker compose ps >&2 || true
    exit 1
  fi
  sleep 5
  elapsed=$((elapsed + 5))
done
echo "Stack ready at $url"
