$ErrorActionPreference = 'Stop'
$rootDir = Split-Path -Parent $PSScriptRoot

$requiredFiles = @(
    'frontend/package.json',
    'frontend/playwright.config.ts',
    'frontend/nginx.conf',
    'frontend/e2e/phase10.spec.ts',
    '.github/workflows/ci.yml',
    'docker-compose.yml',
    'infra/terraform/versions.tf'
)

foreach ($relativePath in $requiredFiles) {
    $path = Join-Path $rootDir $relativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Missing required Phase 10 artifact: $path"
    }
}

function Invoke-Checked([scriptblock] $Command) {
    & $Command
    if ($LASTEXITCODE -ne 0) { throw "Command failed with exit code $LASTEXITCODE" }
}

Push-Location (Join-Path $rootDir 'frontend')
try {
    Invoke-Checked { npm.cmd run lint }
    Invoke-Checked { npm.cmd run typecheck }
    Invoke-Checked { npm.cmd test -- --run }
    Invoke-Checked { npm.cmd run build }
} finally {
    Pop-Location
}

Push-Location $rootDir
try {
    Invoke-Checked { .\mvnw.cmd --batch-mode test }

    if (-not $env:DB_PASSWORD) { $env:DB_PASSWORD = 'phase10_check_only' }
    if (-not $env:NOTIFICATION_DB_PASSWORD) { $env:NOTIFICATION_DB_PASSWORD = 'phase10_notification_check_only' }
    if (-not $env:JWT_SIGNING_SECRET) { $env:JWT_SIGNING_SECRET = 'phase10_check_secret_at_least_32_bytes_long' }
    Invoke-Checked { docker compose config --quiet }
} finally {
    Pop-Location
}

if ($env:RUN_E2E -eq 'true') {
    Push-Location (Join-Path $rootDir 'frontend')
    try {
        Invoke-Checked { npm.cmd run test:e2e }
    } finally {
        Pop-Location
    }
}

Write-Output 'Phase 10 validation passed.'
