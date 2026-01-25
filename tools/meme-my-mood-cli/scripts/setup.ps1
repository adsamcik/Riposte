#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Set up the meme-my-mood-cli development environment.

.DESCRIPTION
    Creates a Python virtual environment and installs dependencies.
    Enforces venv usage for consistent development.

.EXAMPLE
    .\scripts\setup.ps1
#>

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$VenvPath = Join-Path $ProjectRoot ".venv"
$RequirePython = "3.10"

Write-Host "Setting up meme-my-mood-cli..." -ForegroundColor Cyan
Write-Host ""

# Check Python version
$pythonCmd = Get-Command python -ErrorAction SilentlyContinue
if (-not $pythonCmd) {
    Write-Host "Error: Python not found. Please install Python $RequirePython or later." -ForegroundColor Red
    exit 1
}

$pythonVersion = python -c "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')"
Write-Host "Found Python $pythonVersion" -ForegroundColor Green

# Check if venv already exists
if (Test-Path $VenvPath) {
    Write-Host "Virtual environment already exists at $VenvPath" -ForegroundColor Yellow
    $response = Read-Host "Recreate? (y/N)"
    if ($response -eq "y" -or $response -eq "Y") {
        Remove-Item -Recurse -Force $VenvPath
    } else {
        Write-Host "Using existing virtual environment." -ForegroundColor Green
    }
}

# Create virtual environment if needed
if (-not (Test-Path $VenvPath)) {
    Write-Host "Creating virtual environment..." -ForegroundColor Cyan
    python -m venv $VenvPath
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Error: Failed to create virtual environment." -ForegroundColor Red
        exit 1
    }
    Write-Host "Virtual environment created at $VenvPath" -ForegroundColor Green
}

# Activate and install
$activateScript = Join-Path $VenvPath "Scripts\Activate.ps1"
Write-Host "Activating virtual environment..." -ForegroundColor Cyan
. $activateScript

Write-Host "Upgrading pip..." -ForegroundColor Cyan
python -m pip install --upgrade pip

Write-Host "Installing meme-my-mood-cli in development mode..." -ForegroundColor Cyan
Push-Location $ProjectRoot
pip install -e ".[dev]"
Pop-Location

if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to install dependencies." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=" * 60 -ForegroundColor Green
Write-Host "Setup complete!" -ForegroundColor Green
Write-Host "=" * 60 -ForegroundColor Green
Write-Host ""
Write-Host "To activate the virtual environment, run:" -ForegroundColor Cyan
Write-Host "  .venv\Scripts\Activate.ps1" -ForegroundColor White
Write-Host ""
Write-Host "Then you can use the CLI:" -ForegroundColor Cyan
Write-Host "  meme-cli --help" -ForegroundColor White
Write-Host "  meme-cli auth login" -ForegroundColor White
Write-Host "  meme-cli annotate ./my-memes" -ForegroundColor White
Write-Host ""
