<#
.SYNOPSIS
Verify backend environment before startup. Checks credentials, Postgres connectivity, and LLM provider config.

.DESCRIPTION
Runs a series of checks:
1. Required env vars (DB_USERNAME, DB_PASSWORD, GITHUB_MODELS_TOKEN)
2. Postgres reachability on configured host/port
3. LLM_PROVIDER override (warns if non-default)
4. Java and Maven availability

Exits with code 0 (all pass) or non-zero (any fail). Safe to source in startup scripts.

.EXAMPLE
  .\check-env.ps1
  $LASTEXITCODE  # 0 = ready, non-zero = blocked

.EXAMPLE
  # In a batch launcher:
  powershell -NoProfile -ExecutionPolicy Bypass -File backend\scripts\check-env.ps1
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
  mvn spring-boot:run
#>

param(
    [string]$DbHost = "localhost",
    [int]$DbPort = 5432,
    [string]$DbName = "codesense",
    [string]$DefaultLlmProvider = "github-models"
)

$ErrorActionPreference = "Continue"
$checks = @()
$allPassed = $true

# ---------------------------------------------------------------------------
# Helper: add a check result
# ---------------------------------------------------------------------------
function addCheck {
    param([string]$name, [System.Object]$passed, [string]$detail)
    if ($null -eq $passed) {
        $status = "[SKIP]"
        $color = "Yellow"
    } elseif ($passed) {
        $status = "[PASS]"
        $color = "Green"
    } else {
        $status = "[FAIL]"
        $color = "Red"
        $script:allPassed = $false
    }
    Write-Host "$status  $name" -ForegroundColor $color
    if ($detail) { Write-Host "       $detail" -ForegroundColor Gray }
    $script:checks += @{ name = $name; passed = $passed; detail = $detail }
}

# ---------------------------------------------------------------------------
# Check 1: Java
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "=== JAVA ===" -ForegroundColor Cyan
$javaExe = $null
$javaVersion = $null
try {
    $javaExe = (Get-Command java -ErrorAction Stop).Source
    $javaVersion = java -version 2>&1 | Select-Object -First 1
    addCheck "Java available" $true "$(Split-Path $javaExe -Leaf) at $javaExe"
} catch {
    addCheck "Java available" $false "java not in PATH. Set JAVA_HOME or add JDK bin/ to PATH."
}

# ---------------------------------------------------------------------------
# Check 2: Maven
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "=== MAVEN ===" -ForegroundColor Cyan
$mvnExe = $null
try {
    $mvnExe = (Get-Command mvn -ErrorAction Stop).Source
    $mvnVersion = & mvn -v 2>&1 | Select-Object -First 1
    addCheck "Maven available" $true "$(Split-Path $mvnExe -Leaf) at $mvnExe"
} catch {
    $m2Wrapper = (Get-ChildItem "$env:USERPROFILE\.m2\wrapper\dists" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1)
    if ($m2Wrapper) {
        Write-Host "  (Found ~/.m2/wrapper/ - Maven will auto-download on first mvn run)" -ForegroundColor DarkYellow
        addCheck "Maven available" $true "Will download from ~/.m2/wrapper cache"
    } else {
        addCheck "Maven available" $false "mvn not in PATH and no ~/.m2/wrapper cache found."
    }
}

# ---------------------------------------------------------------------------
# Check 3: Database credentials
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "=== DATABASE CREDENTIALS ===" -ForegroundColor Cyan

$dbUser = $env:DB_USERNAME
$dbPass = $env:DB_PASSWORD

if ($dbUser) {
    addCheck "DB_USERNAME set" $true $dbUser
} else {
    addCheck "DB_USERNAME set" $false "export DB_USERNAME=<role>"
}

if ($dbPass) {
    addCheck "DB_PASSWORD set" $true "****"
} else {
    addCheck "DB_PASSWORD set" $false "export DB_PASSWORD=<password>"
}

# ---------------------------------------------------------------------------
# Check 4: Postgres connectivity
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "=== POSTGRES CONNECTIVITY ===" -ForegroundColor Cyan

$psqlExe = $null
try {
    $psqlExe = (Get-Command psql -ErrorAction Stop).Source
} catch {
    # psql not in PATH; try common locations
    $pgPaths = @(
        "C:\Program Files\PostgreSQL\16\bin\psql.exe",
        "C:\Program Files\PostgreSQL\15\bin\psql.exe",
        "$env:USERPROFILE\pgsql-portable\pgsql\bin\psql.exe"
    )
    foreach ($p in $pgPaths) {
        if (Test-Path $p) {
            $psqlExe = $p
            break
        }
    }
}

if ($psqlExe) {
    Write-Host "  Using psql at $psqlExe" -ForegroundColor Gray
    try {
        $env:PGPASSWORD = $dbPass
        $result = & $psqlExe -h $DbHost -p $DbPort -U $dbUser -d $DbName -c "SELECT 1;" 2>&1
        Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue

        if ($LASTEXITCODE -eq 0) {
            addCheck "Postgres reachable" $true "Connected to $DbHost`:$DbPort/$DbName as $dbUser"
        } else {
            addCheck "Postgres reachable" $false "Connection failed: $($result -join ' ')"
        }
    } catch {
        Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
        addCheck "Postgres reachable" $false "psql command failed: $_"
    }
} else {
    Write-Host "  (psql not found in PATH or common locations - skipping connectivity check)" -ForegroundColor DarkYellow
    addCheck "Postgres reachable" $null "Skipped (psql not available; verify manually: host=$DbHost port=$DbPort user=$dbUser)"
}

# ---------------------------------------------------------------------------
# Check 5: LLM provider & token
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "=== LLM PROVIDER ===" -ForegroundColor Cyan

$llmProvider = if ($env:LLM_PROVIDER) { $env:LLM_PROVIDER } else { $DefaultLlmProvider }
$llmToken = $null

if ($env:LLM_PROVIDER -and $env:LLM_PROVIDER -ne $DefaultLlmProvider) {
    Write-Host "  (!) LLM_PROVIDER override active" -ForegroundColor Yellow
    addCheck "LLM provider" $true "$env:LLM_PROVIDER (overrides default $DefaultLlmProvider)"
} else {
    addCheck "LLM provider" $true "$llmProvider (default, from application.yml)"
}

# Check token based on provider
switch ($llmProvider) {
    "github-models" {
        $llmToken = $env:GITHUB_MODELS_TOKEN
        $tokenName = "GITHUB_MODELS_TOKEN"
    }
    "anthropic" {
        $llmToken = $env:ANTHROPIC_API_KEY
        $tokenName = "ANTHROPIC_API_KEY"
    }
    "gemini" {
        $llmToken = $env:GEMINI_API_KEY
        $tokenName = "GEMINI_API_KEY"
    }
    "ollama" {
        Write-Host "  (ollama is local, requires no token)" -ForegroundColor Gray
        addCheck "$llmProvider token" $true "N/A (local service)"
        $llmToken = "N/A"
    }
    default {
        addCheck "LLM token" $false "Unknown provider: $llmProvider"
    }
}

if ($llmToken -and $llmProvider -ne "ollama") {
    addCheck "$llmProvider token" $true "****"
} elseif (-not $llmToken -and $llmProvider -ne "ollama") {
    addCheck "$llmProvider token" $false "export $tokenName=<your-token>"
}

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "=== SUMMARY ===" -ForegroundColor Cyan
$passCount = ($checks | Where-Object { $_.passed -eq $true }).Count
$failCount = ($checks | Where-Object { $_.passed -eq $false }).Count
$skipCount = ($checks | Where-Object { $_.passed -eq $null }).Count
$totalCount = $checks.Count

Write-Host "$passCount/$totalCount passed" -ForegroundColor Green
if ($failCount -gt 0) {
    Write-Host "$failCount failed" -ForegroundColor Red
}
if ($skipCount -gt 0) {
    Write-Host "$skipCount skipped" -ForegroundColor Yellow
}

Write-Host ""
if ($allPassed) {
    Write-Host "[OK] Environment is ready. You can start the backend." -ForegroundColor Green
    exit 0
} else {
    Write-Host "[ERROR] One or more checks failed. Fix the issues above before starting." -ForegroundColor Red
    exit 1
}
