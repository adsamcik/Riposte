# Installs Git hooks from the tracked hooks/ directory.
# Usage: .\hooks\install.ps1

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Split-Path -Parent $ScriptDir
$HooksDir = Join-Path $RepoRoot ".git\hooks"

Write-Host "Installing Git hooks..."

Get-ChildItem -Path $ScriptDir -File | Where-Object {
    $_.Name -notin @("install.sh", "install.ps1")
} | ForEach-Object {
    Copy-Item $_.FullName -Destination (Join-Path $HooksDir $_.Name) -Force
    Write-Host "  ✅ Installed $($_.Name)"
}

Write-Host "Done."
