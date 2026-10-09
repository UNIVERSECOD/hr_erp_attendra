# Real Docker backup/restore smoke test using a disposable, network-isolated database.
# Existing Attendra containers, volumes, credentials and devices are never used.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$testId = [Guid]::NewGuid().ToString('N')
$fixtureRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot "build/backup-docker-tests-$testId"))
$global:attendraDockerTestExecutable = (Get-Command docker.exe -CommandType Application | Select-Object -First 1).Source
$global:attendraDockerTestContainer = "attendra-backup-test-$testId"
$containerCreated = $false

function Invoke-TestDocker {
    & $global:attendraDockerTestExecutable @args
    if ($LASTEXITCODE -ne 0) { throw "Test Docker command failed: $($args[0])" }
}

# The production script uses fixed container names. Route only its known operations
# to our disposable container; all dump/copy/inspect commands are real Docker calls.
function docker {
    $mapped = @($args)
    switch ($mapped[0]) {
        'inspect' {
            if ($mapped[-1] -notin @('hic_postgres', 'hic_backend')) { throw 'Unexpected inspect target' }
            $mapped[-1] = $global:attendraDockerTestContainer
        }
        'exec' {
            if ($mapped[1] -ne 'hic_postgres' -or $mapped[2] -notin @('pg_dump', 'rm')) { throw 'Unexpected exec target' }
            $mapped[1] = $global:attendraDockerTestContainer
        }
        'cp' {
            if ($mapped[1] -notmatch '^hic_(postgres|backend):/') { throw 'Unexpected copy source' }
            $mapped[1] = $mapped[1] -replace '^hic_(postgres|backend):', ($global:attendraDockerTestContainer + ':')
        }
        default { throw "Unexpected backup Docker command: $($mapped[0])" }
    }
    & $global:attendraDockerTestExecutable @mapped
    $global:LASTEXITCODE = $LASTEXITCODE
}

try {
    New-Item -ItemType Directory -Path (Join-Path $fixtureRoot 'runtime') -Force | Out-Null
    $backupRoot = Join-Path $fixtureRoot ('backups-' + [char]0x018F + 'razi')
    $settings = @{ enabled = $true; folderPath = $backupRoot } | ConvertTo-Json
    [IO.File]::WriteAllText((Join-Path $fixtureRoot 'runtime/backup-settings.json'), $settings, (New-Object Text.UTF8Encoding($false)))
    [IO.File]::WriteAllText((Join-Path $fixtureRoot '.env'), 'TEST_FIXTURE_ONLY=true')

    # tmpfs holds all database data; no named volume, host database or published port.
    Invoke-TestDocker run -d --name $global:attendraDockerTestContainer --network none `
        --tmpfs /var/lib/postgresql/data --env POSTGRES_HOST_AUTH_METHOD=trust `
        --env POSTGRES_USER=hic_user --env POSTGRES_DB=hic_backend `
        --health-cmd 'pg_isready -U hic_user -d hic_backend' --health-interval 1s `
        --health-timeout 2s --health-retries 30 postgres:15-alpine | Out-Null
    $containerCreated = $true
    $ready = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        $health = Invoke-TestDocker inspect --format '{{.State.Health.Status}}' $global:attendraDockerTestContainer
        if ($health -eq 'healthy') { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'Disposable PostgreSQL did not become healthy' }
    Invoke-TestDocker exec $global:attendraDockerTestContainer createdb -U hic_user hic_isapi
    foreach ($database in @('hic_backend', 'hic_isapi')) {
        Invoke-TestDocker exec $global:attendraDockerTestContainer psql -U hic_user -d $database -v ON_ERROR_STOP=1 `
            -c "CREATE TABLE backup_probe (id integer PRIMARY KEY, label text); INSERT INTO backup_probe VALUES (1, 'fixture'), (2, 'restore');" | Out-Null
    }
    Invoke-TestDocker exec $global:attendraDockerTestContainer sh -c 'mkdir -p /app/uploads/faces && printf fixture-photo > /app/uploads/faces/probe.txt'

    & (Join-Path $projectRoot 'scripts/backup-attendra.ps1') -ProjectRoot $fixtureRoot -Force
    $statusPath = Join-Path $fixtureRoot 'runtime/backup-status.json'
    $status = Get-Content -LiteralPath $statusPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($status.status -ne 'SUCCESS') { throw 'Real Docker backup did not succeed' }
    $completed = @(Get-ChildItem -LiteralPath $backupRoot -Directory)
    if ($completed.Count -ne 1) { throw 'Expected one completed backup' }
    $backupPath = $completed[0].FullName
    if ([IO.File]::ReadAllText((Join-Path $backupPath 'faces/probe.txt')) -ne 'fixture-photo') { throw 'Face fixture changed' }
    if ([IO.File]::ReadAllText((Join-Path $backupPath '.env.backup')) -ne 'TEST_FIXTURE_ONLY=true') { throw 'Environment fixture changed' }

    foreach ($database in @('hic_backend', 'hic_isapi')) {
        $restoreDatabase = "restored_$database"
        Invoke-TestDocker exec $global:attendraDockerTestContainer createdb -U hic_user $restoreDatabase
        Invoke-TestDocker cp (Join-Path $backupPath "$database.dump") ($global:attendraDockerTestContainer + ":/tmp/restore-$database.dump")
        Invoke-TestDocker exec $global:attendraDockerTestContainer pg_restore -U hic_user --exit-on-error -d $restoreDatabase "/tmp/restore-$database.dump"
        $rows = Invoke-TestDocker exec $global:attendraDockerTestContainer psql -U hic_user -d $restoreDatabase -Atc 'SELECT id, label FROM backup_probe ORDER BY id'
        if (($rows -join ',') -ne '1|fixture,2|restore') { throw "Restored fixture mismatch: $database" }
    }
    & (Join-Path $projectRoot 'scripts/backup-attendra.ps1') -ProjectRoot $fixtureRoot
    $status = Get-Content -LiteralPath $statusPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($status.status -ne 'SKIPPED') { throw 'Real backup was not recognized on second run' }
    Write-Host "PASS: real Docker backup and restore of both fixture databases; face/env copies; Unicode path; daily skip. PowerShell $($PSVersionTable.PSVersion)."
} finally {
    if ($containerCreated) {
        # Exact unique name created by this test; never compose down or delete volumes.
        Invoke-TestDocker rm -f $global:attendraDockerTestContainer | Out-Null
    }
    $allowedParent = [IO.Path]::GetFullPath((Join-Path $projectRoot 'build'))
    if ([IO.Path]::GetDirectoryName($fixtureRoot) -ne $allowedParent -or
        [IO.Path]::GetFileName($fixtureRoot) -ne "backup-docker-tests-$testId") { throw 'Unsafe fixture cleanup path' }
    if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
    Remove-Variable -Name attendraDockerTestExecutable, attendraDockerTestContainer -Scope Global
}
