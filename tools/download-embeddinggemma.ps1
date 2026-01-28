#!/usr/bin/env pwsh
# Download EmbeddingGemma model files from HuggingFace
# Requires: huggingface-cli installed and authenticated with access to the gated model
#
# Prerequisites:
# 1. pip install huggingface_hub
# 2. huggingface-cli login
# 3. Go to https://huggingface.co/litert-community/embeddinggemma-300m and accept the license

param(
    [string]$OutputDir = "$PSScriptRoot\..\app\src\main\assets\embedding_models",
    [string]$SeqLength = "512",
    [switch]$AllVariants,
    [switch]$Force
)

$ErrorActionPreference = "Stop"

$REPO_ID = "litert-community/embeddinggemma-300m"
$TOKENIZER_FILE = "sentencepiece.model"

# Model variants - generic + platform-specific
$GENERIC_MODEL = "embeddinggemma-300M_seq${SeqLength}_mixed-precision.tflite"
$PLATFORM_MODELS = @(
    "embeddinggemma-300M_seq${SeqLength}_mixed-precision.qualcomm.sm8550.tflite",  # Snapdragon 8 Gen 2
    "embeddinggemma-300M_seq${SeqLength}_mixed-precision.qualcomm.sm8650.tflite",  # Snapdragon 8 Gen 3
    "embeddinggemma-300M_seq${SeqLength}_mixed-precision.qualcomm.sm8750.tflite",  # Snapdragon 8 Gen 4 (Elite)
    "embeddinggemma-300M_seq${SeqLength}_mixed-precision.qualcomm.sm8850.tflite",  # Snapdragon 8 Gen 5
    "embeddinggemma-300M_seq${SeqLength}_mixed-precision.mediatek.mt6991.tflite",  # Dimensity 9300
    "embeddinggemma-300M_seq${SeqLength}_mixed-precision.mediatek.mt6993.tflite"   # Dimensity 9400
)

Write-Host "🦎 EmbeddingGemma Model Downloader" -ForegroundColor Cyan
Write-Host "=================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Repository: $REPO_ID"
Write-Host "Sequence length: $SeqLength tokens"
Write-Host "Output directory: $OutputDir"
Write-Host "All variants: $AllVariants"
Write-Host ""

# Check if huggingface-cli is installed
try {
    $null = Get-Command huggingface-cli -ErrorAction Stop
} catch {
    Write-Host "❌ huggingface-cli not found!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Install it with: pip install huggingface_hub" -ForegroundColor Yellow
    Write-Host "Then login with: huggingface-cli login" -ForegroundColor Yellow
    exit 1
}

# Create output directory
if (-not (Test-Path $OutputDir)) {
    Write-Host "📁 Creating output directory..."
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

function Download-File {
    param([string]$FileName)
    
    $filePath = Join-Path $OutputDir $FileName
    
    if ((Test-Path $filePath) -and -not $Force) {
        Write-Host "✅ Already exists: $FileName" -ForegroundColor Green
        return $true
    }
    
    Write-Host "⬇️  Downloading: $FileName..." -ForegroundColor Yellow
    huggingface-cli download $REPO_ID $FileName --local-dir $OutputDir --local-dir-use-symlinks False
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ Failed to download: $FileName" -ForegroundColor Red
        return $false
    }
    
    Write-Host "✅ Downloaded: $FileName" -ForegroundColor Green
    return $true
}

# Download tokenizer (always required)
Write-Host "📦 Downloading tokenizer..." -ForegroundColor Cyan
if (-not (Download-File $TOKENIZER_FILE)) {
    Write-Host ""
    Write-Host "Make sure you have:" -ForegroundColor Yellow
    Write-Host "1. Logged in: huggingface-cli login" -ForegroundColor Yellow
    Write-Host "2. Accepted the license at: https://huggingface.co/litert-community/embeddinggemma-300m" -ForegroundColor Yellow
    exit 1
}

# Download generic model (always required as fallback)
Write-Host ""
Write-Host "📦 Downloading generic model (fallback)..." -ForegroundColor Cyan
if (-not (Download-File $GENERIC_MODEL)) {
    exit 1
}

# Download platform-specific models if requested
if ($AllVariants) {
    Write-Host ""
    Write-Host "📦 Downloading platform-specific models..." -ForegroundColor Cyan
    
    foreach ($model in $PLATFORM_MODELS) {
        Download-File $model | Out-Null
    }
}

Write-Host ""
Write-Host "🎉 Download complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Files in $OutputDir :" -ForegroundColor Cyan

# Show file sizes
$totalSize = 0
Get-ChildItem $OutputDir -Filter "*.tflite" | ForEach-Object {
    $sizeMB = [math]::Round($_.Length / 1MB, 2)
    $totalSize += $_.Length
    Write-Host "  $($_.Name): $sizeMB MB"
}
Get-ChildItem $OutputDir -Filter "*.model" | ForEach-Object {
    $sizeMB = [math]::Round($_.Length / 1MB, 2)
    $totalSize += $_.Length
    Write-Host "  $($_.Name): $sizeMB MB"
}

$totalMB = [math]::Round($totalSize / 1MB, 2)
Write-Host ""
Write-Host "Total: $totalMB MB" -ForegroundColor Cyan

if ($AllVariants) {
    Write-Host ""
    Write-Host "⚠️  Note: Including all variants will add ~1.3GB to your APK!" -ForegroundColor Yellow
    Write-Host "Consider using Android App Bundles (AAB) with device targeting," -ForegroundColor Yellow
    Write-Host "or PODAI (Play for On-Device AI) for production." -ForegroundColor Yellow
}
