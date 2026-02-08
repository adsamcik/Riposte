# Pre-commit hook for Riposte (PowerShell)
# This hook runs code quality checks before allowing a commit

$ErrorActionPreference = "Stop"

Write-Host "🔍 Running pre-commit checks..." -ForegroundColor Cyan

# Function to print colored messages
function Write-Error-Custom {
    param([string]$Message)
    Write-Host "❌ $Message" -ForegroundColor Red
}

function Write-Success {
    param([string]$Message)
    Write-Host "✅ $Message" -ForegroundColor Green
}

function Write-Warning-Custom {
    param([string]$Message)
    Write-Host "⚠️  $Message" -ForegroundColor Yellow
}

function Write-Info {
    param([string]$Message)
    Write-Host "ℹ️  $Message" -ForegroundColor White
}

# Check if we're in the project root
if (-not (Test-Path "gradlew.bat")) {
    Write-Error-Custom "Not in project root directory"
    exit 1
}

# Get list of staged Kotlin files
$stagedKtFiles = git diff --cached --name-only --diff-filter=ACMR | Where-Object { $_ -match '\.kt$' }

if ($stagedKtFiles.Count -eq 0) {
    Write-Info "No Kotlin files staged, skipping checks"
    exit 0
}

Write-Info "Found $($stagedKtFiles.Count) staged Kotlin file(s)"

# Run ktlint format on staged files
Write-Info "Running ktlint format..."
try {
    & .\gradlew.bat ktlintFormat --quiet
    if ($LASTEXITCODE -eq 0) {
        Write-Success "ktlint formatting passed"
        
        # Add formatted files back to staging
        foreach ($file in $stagedKtFiles) {
            if (Test-Path $file) {
                git add $file
            }
        }
    } else {
        throw "ktlint failed"
    }
} catch {
    Write-Error-Custom "ktlint formatting failed"
    Write-Info "Run '.\gradlew.bat ktlintFormat' to fix formatting issues"
    exit 1
}

# Run detekt on changed files (quick check)
Write-Info "Running detekt static analysis..."
try {
    & .\gradlew.bat detekt --quiet
    if ($LASTEXITCODE -eq 0) {
        Write-Success "detekt static analysis passed"
    } else {
        throw "detekt failed"
    }
} catch {
    Write-Error-Custom "detekt found issues"
    Write-Info "Run '.\gradlew.bat detekt' to see detailed report"
    Write-Warning-Custom "You can skip this check with 'git commit --no-verify' (not recommended)"
    exit 1
}

# Check for common issues
Write-Info "Checking for common issues..."

# Check for debug statements
$debugStatements = $stagedKtFiles | ForEach-Object {
    Select-String -Path $_ -Pattern "println|Log\.d|Log\.v" -AllMatches
} | Where-Object { $_ }

if ($debugStatements) {
    Write-Warning-Custom "Found debug statements in staged files:"
    $debugStatements | ForEach-Object { Write-Host "$($_.Filename):$($_.LineNumber): $($_.Line.Trim())" }
    Write-Info "Consider removing debug statements before committing"
}

# Check for TODO/FIXME comments
$todoComments = $stagedKtFiles | ForEach-Object {
    Select-String -Path $_ -Pattern "TODO|FIXME" -AllMatches
} | Where-Object { $_ }

if ($todoComments) {
    Write-Warning-Custom "Found TODO/FIXME comments:"
    $todoComments | Select-Object -First 5 | ForEach-Object { 
        Write-Host "$($_.Filename):$($_.LineNumber): $($_.Line.Trim())" 
    }
}

# Check for large files (>500KB)
$largeFiles = git diff --cached --name-only --diff-filter=ACMR | Where-Object {
    Test-Path $_ -and (Get-Item $_).Length -gt 512000
}

if ($largeFiles) {
    Write-Warning-Custom "Found large files (>500KB):"
    $largeFiles | ForEach-Object { Write-Host $_ }
    Write-Info "Consider if these files should be committed"
}

Write-Success "All pre-commit checks passed!"
Write-Host ""
exit 0
