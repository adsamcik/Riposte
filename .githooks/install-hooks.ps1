# Install Git hooks for Meme My Mood (PowerShell)

Write-Host "📦 Installing Git hooks..." -ForegroundColor Cyan

# Get the directory where the script is located
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir
$hooksDir = Join-Path $projectRoot ".githooks"
$gitHooksDir = Join-Path $projectRoot ".git\hooks"

# Check if .git directory exists
if (-not (Test-Path (Join-Path $projectRoot ".git"))) {
    Write-Host "❌ Error: .git directory not found. Are you in a Git repository?" -ForegroundColor Red
    exit 1
}

# Create hooks directory if it doesn't exist
New-Item -ItemType Directory -Force -Path $gitHooksDir | Out-Null

# Install pre-commit hook (PowerShell wrapper)
$preCommitHook = @"
#!/bin/sh
# Pre-commit hook wrapper for Windows/PowerShell

# Try to run PowerShell version first
if command -v pwsh >/dev/null 2>&1; then
    pwsh -NoProfile -ExecutionPolicy Bypass -File ".githooks/pre-commit.ps1"
elif command -v powershell >/dev/null 2>&1; then
    powershell -NoProfile -ExecutionPolicy Bypass -File ".githooks/pre-commit.ps1"
else
    # Fall back to bash version
    .githooks/pre-commit
fi
"@

$preCommitPath = Join-Path $gitHooksDir "pre-commit"
Set-Content -Path $preCommitPath -Value $preCommitHook -Encoding UTF8
Write-Host "✅ Installed pre-commit hook" -ForegroundColor Green

# Configure Git to use local hooks directory
git config core.hooksPath .githooks
Write-Host "✅ Configured Git to use .githooks directory" -ForegroundColor Green

Write-Host ""
Write-Host "✅ Git hooks installed successfully!" -ForegroundColor Green
Write-Host ""
Write-Host "Installed hooks:" -ForegroundColor White
Write-Host "  - pre-commit: Runs ktlint and detekt before commits"
Write-Host ""
Write-Host "To bypass hooks (not recommended), use: git commit --no-verify"
