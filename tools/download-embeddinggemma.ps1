#!/usr/bin/env pwsh
# Download EmbeddingGemma model files from HuggingFace into aipacks/ for Play Asset Delivery
# Requires: huggingface-cli installed and authenticated with access to the gated model
#
# Prerequisites:
# 1. pip install huggingface_hub
# 2. huggingface-cli login
# 3. Go to https://huggingface.co/litert-community/embeddinggemma-300m and accept the license

param(
    [string]$SeqLength = "512",
    [switch]$AllVariants,
    [switch]$Force
)

$ErrorActionPreference = "Stop"

$REPO_ID = "litert-community/embeddinggemma-300m"
$TOKENIZER_FILE = "sentencepiece.model"
$ROOT_DIR = "$PSScriptRoot\.."

# Model variants mapped to their aipack output directories
$GENERIC_MODEL = "embeddinggemma-300M_seq${SeqLength}_mixed-precision.tflite"
$GENERIC_DIR = "$ROOT_DIR\aipacks\generic_embedding\src\main\assets\embedding_models"

# SoC-optimized variants: model filename -> asset pack directory name
$SOC_MODELS = [ordered]@{
    "embeddinggemma-300M_seq${SeqLength}_mixed-precision.qualcomm.sm8550.tflite" = "embedding_models#group_qualcomm_sm8550"  # Snapdragon 8 Gen 2
    "embeddinggemma-300M_seq${SeqLength}_mixed-precision.qualcomm.sm8650.tflite" = "embedding_models#group_qualcomm_sm8650"  # Snapdragon 8 Gen 3
    "embeddinggemma-300M_seq${SeqLength}_mixed-precision.qualcomm.sm8750.tflite" = "embedding_models#group_qualcomm_sm8750"  # Snapdragon 8 Gen 4 (Elite)
    "embeddinggemma-300M_seq${SeqLength}_mixed-precision.qualcomm.sm8850.tflite" = "embedding_models#group_qualcomm_sm8850"  # Snapdragon 8 Gen 5
    "embeddinggemma-300M_seq${SeqLength}_mixed-precision.mediatek.mt6991.tflite" = "embedding_models#group_mediatek_mt6991"  # Dimensity 9300
    "embeddinggemma-300M_seq${SeqLength}_mixed-precision.mediatek.mt6993.tflite" = "embedding_models#group_mediatek_mt6993"  # Dimensity 9400
}
$SOC_BASE_DIR = "$ROOT_DIR\aipacks\soc_optimized\src\main\assets"

Write-Host "🦎 EmbeddingGemma Model Downloader" -ForegroundColor Cyan
Write-Host "=================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Repository: $REPO_ID"
Write-Host "Sequence length: $SeqLength tokens"
Write-Host "Generic output: $GENERIC_DIR"
Write-Host "SoC output:     $SOC_BASE_DIR"
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
if (-not (Test-Path $GENERIC_DIR)) {
    Write-Host "📁 Creating generic model directory..."
    New-Item -ItemType Directory -Path $GENERIC_DIR -Force | Out-Null
}

function Download-File {
    param(
        [string]$FileName,
        [string]$OutputDir
    )
    
    $filePath = Join-Path $OutputDir $FileName
    
    if ((Test-Path $filePath) -and -not $Force) {
        Write-Host "✅ Already exists: $FileName" -ForegroundColor Green
        return $true
    }

    if (-not (Test-Path $OutputDir)) {
        New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
    }
    
    Write-Host "⬇️  Downloading: $FileName -> $OutputDir" -ForegroundColor Yellow
    huggingface-cli download $REPO_ID $FileName --local-dir $OutputDir --local-dir-use-symlinks False
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ Failed to download: $FileName" -ForegroundColor Red
        return $false
    }
    
    Write-Host "✅ Downloaded: $FileName" -ForegroundColor Green
    return $true
}

# Download tokenizer + generic model (always required)
Write-Host "📦 Downloading tokenizer..." -ForegroundColor Cyan
if (-not (Download-File $TOKENIZER_FILE $GENERIC_DIR)) {
    Write-Host ""
    Write-Host "Make sure you have:" -ForegroundColor Yellow
    Write-Host "1. Logged in: huggingface-cli login" -ForegroundColor Yellow
    Write-Host "2. Accepted the license at: https://huggingface.co/litert-community/embeddinggemma-300m" -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "📦 Downloading generic model (fallback)..." -ForegroundColor Cyan
if (-not (Download-File $GENERIC_MODEL $GENERIC_DIR)) {
    exit 1
}

# Download SoC-optimized models if requested
if ($AllVariants) {
    Write-Host ""
    Write-Host "📦 Downloading SoC-optimized models..." -ForegroundColor Cyan
    
    foreach ($entry in $SOC_MODELS.GetEnumerator()) {
        $modelFile = $entry.Key
        $assetDir = Join-Path $SOC_BASE_DIR $entry.Value

        # Each SoC group needs its own tokenizer copy
        Download-File $TOKENIZER_FILE $assetDir | Out-Null
        Download-File $modelFile $assetDir | Out-Null
    }
}

Write-Host ""
Write-Host "🎉 Download complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Downloaded models:" -ForegroundColor Cyan

# Show file sizes across all aipack directories
$totalSize = 0
$aipacksDir = "$ROOT_DIR\aipacks"
Get-ChildItem $aipacksDir -Recurse -Include "*.tflite","*.model" | ForEach-Object {
    $sizeMB = [math]::Round($_.Length / 1MB, 2)
    $totalSize += $_.Length
    $relativePath = $_.FullName.Replace("$aipacksDir\", "")
    Write-Host "  $relativePath : $sizeMB MB"
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
