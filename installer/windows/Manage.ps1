param([ValidateSet('Prepare','Install','Uninstall')][string]$Action,
      [Parameter(Mandatory=$true)][string]$InstallRoot,
      [string]$DataRoot = "$env:ProgramData\Attendra", [string]$Version = '1.0.0')
. "$PSScriptRoot\Runtime.ps1"
$InstallRoot = [IO.Path]::GetFullPath($InstallRoot).TrimEnd('\')
$DataRoot = [IO.Path]::GetFullPath($DataRoot).TrimEnd('\')
$names = @('Database', 'Isapi', 'Backend', 'Web')
if ($InstallRoot -eq [IO.Path]::GetPathRoot($InstallRoot).TrimEnd('\') -or
    $DataRoot.StartsWith($InstallRoot + '\', [StringComparison]::OrdinalIgnoreCase) -or
    $InstallRoot.StartsWith($DataRoot + '\', [StringComparison]::OrdinalIgnoreCase) -or $DataRoot -eq $InstallRoot) {
    throw 'Program and data directories must be separate and must not be a drive root.'
}
function Stop-Components([switch]$KeepDatabase) {
    foreach ($name in @('Web','Backend','Isapi','Database')) {
        if ($name -eq 'Database' -and $KeepDatabase) { continue }
        $service = Get-Service "Attendra$name" -ErrorAction SilentlyContinue
        if ($service -and $service.Status -ne 'Stopped') {
            Stop-Service $service -ErrorAction Stop
            $service.WaitForStatus('Stopped', [TimeSpan]::FromSeconds(60))
        }
    }
}
try {
    if ($Action -eq 'Prepare') {
        if (Test-Path "$DataRoot\native.json") {
            $config = Get-Content "$DataRoot\native.json" -Raw -Encoding UTF8 | ConvertFrom-Json
            if ($config.installRoot -ne $InstallRoot) { throw 'Choose the existing installation directory.' }
            if (Test-Path "$DataRoot\version.txt") {
                if ([version](Get-Content "$DataRoot\version.txt") -gt [version]$Version) { throw 'Downgrades are not supported.' }
            }
            if (Test-Path "$DataRoot\postgres\PG_VERSION") {
                # Freeze application writes while taking both databases and photos.
                Stop-Components -KeepDatabase
                Start-Service AttendraDatabase
                & powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$InstallRoot\scripts\backup-attendra.ps1" -ProjectRoot $DataRoot -Force -Required
                if ($LASTEXITCODE) { throw 'Pre-upgrade backup failed. Upgrade cancelled.' }
                Stop-Components
            }
        } else {
            foreach ($name in $names) { if (Get-Service "Attendra$name" -ErrorAction SilentlyContinue) { throw 'Service name conflict. Existing service was not changed.' } }
            Assert-FreePorts @(3000,15432,18080,18081)
        }
        exit 0
    }
    if ($Action -eq 'Uninstall') {
        Stop-Components
        Unregister-ScheduledTask -TaskName 'Attendra Native Backup' -Confirm:$false -ErrorAction SilentlyContinue
        Get-NetFirewallRule -Name 'AttendraNativeWeb' -ErrorAction SilentlyContinue | Remove-NetFirewallRule
        foreach ($name in @('Web','Backend','Isapi','Database')) {
            if (Get-Service "Attendra$name" -ErrorAction SilentlyContinue) {
                & "$InstallRoot\services\Attendra$name.exe" uninstall
                if ($LASTEXITCODE) { throw 'Service removal failed; data is preserved.' }
            }
        }
        $manifest = Get-Content "$InstallRoot\files.json" -Raw -Encoding UTF8 | ConvertFrom-Json
        foreach ($item in $manifest) {
            $path = [IO.Path]::GetFullPath((Join-Path $InstallRoot $item.path))
            if (-not $path.StartsWith($InstallRoot + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid installed file manifest.' }
        }
        foreach ($item in $manifest) { Remove-Item -LiteralPath (Join-Path $InstallRoot $item.path) -Force -ErrorAction SilentlyContinue }
        foreach ($name in $names) {
            foreach ($extension in @('exe','xml')) { Remove-Item -LiteralPath "$InstallRoot\services\Attendra$name.$extension" -Force -ErrorAction SilentlyContinue }
        }
        Remove-Item -LiteralPath "$InstallRoot\files.json" -Force
        Get-ChildItem -LiteralPath $InstallRoot -Directory -Recurse | Sort-Object { $_.FullName.Length } -Descending | ForEach-Object {
            if (-not @(Get-ChildItem -LiteralPath $_.FullName -Force).Count) { Remove-Item -LiteralPath $_.FullName -Force }
        }
        # Never remove DataRoot, database, encryption keys, photos or backups.
        exit 0
    }
    $manifest = Get-Content "$InstallRoot\files.json" -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($item in $manifest) {
        $path = [IO.Path]::GetFullPath((Join-Path $InstallRoot $item.path))
        if (-not $path.StartsWith($InstallRoot + '\', [StringComparison]::OrdinalIgnoreCase) -or
            (Get-FileHash -LiteralPath $path).Hash -ne $item.sha256) { throw 'Installed payload verification failed.' }
    }
    $config = Initialize-Native $InstallRoot $DataRoot
    Initialize-Database $config
    Write-ServiceFiles $config
    foreach ($name in $names) {
        if (-not (Get-Service "Attendra$name" -ErrorAction SilentlyContinue)) {
            & "$InstallRoot\services\Attendra$name.exe" install
            if ($LASTEXITCODE) { throw "Cannot install Attendra$name service." }
        }
    }
    Start-Service AttendraDatabase
    $deadline = (Get-Date).AddSeconds(60)
    do {
        & "$InstallRoot\postgres\bin\pg_isready.exe" -h 127.0.0.1 -p $config.databasePort -U hic_user *> $null
        if ($LASTEXITCODE -eq 0) { break }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    Ensure-Databases $config
    Start-Service AttendraIsapi
    Wait-Http "http://127.0.0.1:$($config.isapiPort)/api/devices" 120 @{'X-API-Key'=$config.apiKey}
    Start-Service AttendraBackend
    Wait-Http "http://127.0.0.1:$($config.backendPort)/api/health"
    Start-Service AttendraWeb
    Wait-Http "http://127.0.0.1:$($config.webPort)/api/health"
    $arguments = "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$InstallRoot\scripts\backup-attendra.ps1`" -ProjectRoot `"$DataRoot`""
    $taskAction = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $arguments
    $principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount
    $settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Hours 1)
    Register-ScheduledTask -TaskName 'Attendra Native Backup' -Action $taskAction -Principal $principal -Trigger (New-ScheduledTaskTrigger -Daily -At '04:00') -Settings $settings -Force | Out-Null
    if (-not (Get-NetFirewallRule -Name 'AttendraNativeWeb' -ErrorAction SilentlyContinue)) {
        New-NetFirewallRule -Name 'AttendraNativeWeb' -DisplayName 'Attendra web (local network)' -Direction Inbound -Action Allow -Protocol TCP -LocalPort $config.webPort -Profile Private,Domain -RemoteAddress LocalSubnet | Out-Null
    }
    Write-Utf8 "$DataRoot\version.txt" $Version
    Write-Output 'Attendra installed and health checks passed.'
} catch {
    if ($Action -eq 'Prepare' -and (Test-Path "$DataRoot\native.json")) {
        foreach ($name in $names) { if (Get-Service "Attendra$name" -ErrorAction SilentlyContinue) { Start-Service "Attendra$name" -ErrorAction SilentlyContinue } }
    }
    if (Test-Path "$DataRoot\logs") { Write-Utf8 "$DataRoot\logs\setup-error.txt" $_.Exception.Message }
    Write-Error $_.Exception.Message
    exit 1
}
