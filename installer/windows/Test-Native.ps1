param([Parameter(Mandatory=$true)][string]$Payload)
. "$PSScriptRoot\Runtime.ps1"
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$data = Join-Path $repo ("build\native-smoke-" + [Guid]::NewGuid().ToString('N'))
$children = @()
function Check([bool]$Condition, [string]$Label) { if (-not $Condition) { throw "FAIL: $Label" }; Write-Output "PASS: $Label" }
try {
    $config = Initialize-Native $Payload $data 39100 39101 39102 39103
    $again = Initialize-Native $Payload $data 39100 39101 39102 39103
    Check ($config.encryptionKey -eq $again.encryptionKey) 'Repair preserves encryption keys'
    Initialize-Database $config
    Write-ServiceFiles $config
    foreach ($name in @('Database','Backend','Isapi','Web')) {
        $xml = [xml](Get-Content "$Payload\services\Attendra$name.xml" -Raw)
        Check ($xml.service.serviceaccount.username -eq 'NT AUTHORITY\LocalService') "$name uses LocalService"
    }
    $starter = Start-Process "$Payload\postgres\bin\pg_ctl.exe" -ArgumentList "-D `"$data\postgres`" -l `"$data\logs\postgres.log`" -w start" -WindowStyle Hidden -PassThru
    if (-not $starter.WaitForExit(60000) -or $starter.ExitCode -ne 0) { throw 'Test PostgreSQL did not start' }
    Ensure-Databases $config
    foreach ($component in @('Isapi','Backend')) {
        $args = "-NoProfile -ExecutionPolicy Bypass -File `"$Payload\scripts\Start-Component.ps1`" -Component $component -DataRoot `"$data`""
        $children += Start-Process powershell.exe -ArgumentList $args -WindowStyle Hidden -PassThru -RedirectStandardOutput "$data\logs\$component.stdout.log" -RedirectStandardError "$data\logs\$component.stderr.log"
    }
    Wait-Http 'http://127.0.0.1:39103/api/devices' 120 @{'X-API-Key'=$config.apiKey}
    Wait-Http 'http://127.0.0.1:39102/api/health'
    Check $true 'Both Java services start against new native databases'
    $children += Start-Process "$Payload\web\caddy.exe" -ArgumentList "run --config `"$data\Caddyfile`" --adapter caddyfile" -WindowStyle Hidden -PassThru -RedirectStandardOutput "$data\logs\web.stdout.log" -RedirectStandardError "$data\logs\web.stderr.log"
    Wait-Http 'http://127.0.0.1:39100/api/health'
    Check ((Invoke-WebRequest http://127.0.0.1:39100/reports -UseBasicParsing).Content -match '<div id="root">') 'Web SPA routes and API proxy work'
    $env:PGPASSWORD = $config.databasePassword
    $pg = "$Payload\postgres\bin\psql.exe"
    & $pg -h 127.0.0.1 -p 39101 -U hic_user -d hic_backend -v ON_ERROR_STOP=1 -c "create table installer_probe(id int primary key, value text); insert into installer_probe values(1,'native-backup-test');" | Out-Null
    Write-Utf8 "$data\faces\synthetic.txt" 'Synthetic photo preservation marker'
    Initialize-Database $config
    Check ((& $pg -h 127.0.0.1 -p 39101 -U hic_user -d hic_backend -Atc 'select count(*) from installer_probe') -eq '1') 'Database initialization preserves existing data'
    Write-Utf8 "$data\runtime\backup-settings.json" (@{enabled=$false; folderPath="$data\backups"; retentionDays=183} | ConvertTo-Json)
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$repo\scripts\backup-attendra.ps1" -ProjectRoot $data -Force -Required
    if ($LASTEXITCODE) { throw 'Native backup failed' }
    $status = Get-Content "$data\runtime\backup-status.json" -Raw | ConvertFrom-Json
    Check ($status.status -eq 'SUCCESS') 'Mandatory upgrade backup works even when daily backup is disabled'
    $backup = Get-ChildItem "$data\backups" -Directory | Where-Object Name -Match '^\d{8}-\d{6}$' | Select-Object -Last 1
    & $pg -h 127.0.0.1 -p 39101 -U hic_user -d postgres -c 'create database restore_probe' | Out-Null
    & "$Payload\postgres\bin\pg_restore.exe" -h 127.0.0.1 -p 39101 -U hic_user -d restore_probe --exit-on-error "$($backup.FullName)\hic_backend.dump"
    if ($LASTEXITCODE) { throw 'Restore failed' }
    $value = & $pg -h 127.0.0.1 -p 39101 -U hic_user -d restore_probe -Atc 'select value from installer_probe where id=1'
    Check ($value -eq 'native-backup-test') 'Database dump restores into separate native database'
    & $pg -h 127.0.0.1 -p 39101 -U hic_user -d postgres -c 'create database restore_isapi_probe' | Out-Null
    & "$Payload\postgres\bin\pg_restore.exe" -h 127.0.0.1 -p 39101 -U hic_user -d restore_isapi_probe --exit-on-error "$($backup.FullName)\hic_isapi.dump"
    if ($LASTEXITCODE) { throw 'ISAPI restore failed' }
    Check ((& $pg -h 127.0.0.1 -p 39101 -U hic_user -d restore_isapi_probe -Atc 'select count(*) from flyway_schema_history_isapi where success') -eq '4') 'ISAPI database dump restores with all migrations'
    Check ((Get-FileHash "$data\faces\synthetic.txt").Hash -eq (Get-FileHash "$($backup.FullName)\faces\synthetic.txt").Hash) 'Backup preserves photos'
    $saved = Get-Content "$($backup.FullName)\.env.backup" -Raw | ConvertFrom-Json
    Check ($saved.encryptionKey -eq $config.encryptionKey) 'Backup preserves encryption keys'
    Check ((& $pg -h 127.0.0.1 -p 39101 -U hic_user -d hic_backend -Atc 'select count(*) from flyway_schema_history_backend where success') -eq '42') 'All backend Flyway migrations applied'
    Write-Output "Native smoke passed. Test data: $data"
} finally {
    foreach ($child in $children) {
        if (-not $child.HasExited) { & taskkill.exe /PID $child.Id /T /F *> $null }
    }
    if (Test-Path "$data\postgres\postmaster.pid") {
        $stopper = Start-Process "$Payload\postgres\bin\pg_ctl.exe" -ArgumentList "-D `"$data\postgres`" -m fast -w stop" -WindowStyle Hidden -PassThru
        $null = $stopper.WaitForExit(30000)
    }
    $env:PGPASSWORD = $null
}
