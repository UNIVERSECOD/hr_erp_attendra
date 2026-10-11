$ErrorActionPreference = 'Stop'
$listener = Get-NetTCPConnection -LocalPort 18765 -State Listen -ErrorAction SilentlyContinue
if (-not $listener) {
    Start-Process powershell.exe -WindowStyle Hidden -ArgumentList "-NoProfile -STA -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$PSScriptRoot\backup-folder-picker.ps1`""
}
Start-Process 'http://localhost:3000'
