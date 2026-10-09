# Dependency-free regression tests; compatible with Windows PowerShell 5.1 and PowerShell 7.
# Docker is always mocked. Only disposable fixtures under build/ are modified.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$backupScript = Join-Path $projectRoot 'scripts/backup-attendra.ps1'
$fixtureRoot = Join-Path $projectRoot ('build/backup-tests-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $fixtureRoot -Force | Out-Null
$global:attendraBackupTest = @{
    root = $fixtureRoot
    now = [DateTime]'2026-10-09T12:00:00'
    calls = 0
    sleeps = 0
    mode = 'ready'
}
$script:assertions = 0

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "FAILED: $Message" }
    $script:assertions++
}

function Get-Date { return $global:attendraBackupTest.now }
function Start-Sleep {
    param([int]$Seconds)
    $global:attendraBackupTest.sleeps++
    $global:attendraBackupTest.now = $global:attendraBackupTest.now.AddSeconds($Seconds)
}
function docker {
    $global:attendraBackupTest.calls++
    $global:LASTEXITCODE = 0
    if ($args[0] -eq 'inspect') {
        if ($global:attendraBackupTest.mode -eq 'offline' -or
            ($global:attendraBackupTest.mode -eq 'recover' -and $global:attendraBackupTest.sleeps -eq 0)) {
            # A real native stderr stream reproduces the PowerShell 5.1 regression.
            & "$env:SystemRoot\System32\cmd.exe" /d /c 'echo simulated daemon unavailable 1>&2 & exit /b 1'
            $global:LASTEXITCODE = 1
        } elseif ($global:attendraBackupTest.mode -eq 'empty') {
            return
        } elseif ($global:attendraBackupTest.mode -eq 'exit-failure') {
            'healthy'
            $global:LASTEXITCODE = 1
        } elseif ($args[-1] -eq 'hic_postgres') { 'healthy' } else { 'running' }
    } elseif ($args[0] -eq 'cp') {
        $target = [System.IO.Path]::GetFullPath($args[2])
        if (-not $target.StartsWith($global:attendraBackupTest.root + '\', [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Mock write escaped fixture root'
        }
        if ($args[1] -like '*uploads/faces') {
            New-Item -ItemType Directory -Path $target | Out-Null
        } else {
            $dump = 'PGDMP-mocked-dump'
            if ($global:attendraBackupTest.mode -eq 'corrupt-copy') { $dump = 'XXXXX-mocked-dump' }
            [System.IO.File]::WriteAllText($target, $dump)
        }
    } elseif ($args[0] -eq 'exec' -and $args[2] -eq 'pg_dump') {
        if ($global:attendraBackupTest.mode -in @('dump-failure', 'dump-and-cleanup-failure')) { $global:LASTEXITCODE = 1 }
    } elseif ($args[0] -eq 'exec' -and $args[2] -eq 'rm') {
        if ($global:attendraBackupTest.mode -in @('cleanup-failure', 'dump-and-cleanup-failure')) {
            & "$env:SystemRoot\System32\cmd.exe" /d /c 'echo simulated cleanup unavailable 1>&2 & exit /b 1'
            $global:LASTEXITCODE = 1
        } elseif ($global:attendraBackupTest.mode -eq 'cleanup-exit-failure') {
            $global:LASTEXITCODE = 1
        } elseif ($global:attendraBackupTest.mode -eq 'cleanup-throw') {
            throw 'Simulated cleanup invocation error'
        }
        return
    } else { throw "Unexpected mock Docker command: $($args[0])" }
}

function New-Fixture {
    param([string]$Name)
    $path = Join-Path $fixtureRoot $Name
    New-Item -ItemType Directory -Path (Join-Path $path 'runtime'), (Join-Path $path 'backups') -Force | Out-Null
    @{ enabled = $true; folderPath = (Join-Path $path 'backups') } | ConvertTo-Json |
        Set-Content -LiteralPath (Join-Path $path 'runtime/backup-settings.json') -Encoding UTF8
    return $path
}

function New-Backup {
    param([string]$Project, [string]$Name)
    $path = Join-Path (Join-Path $Project 'backups') $Name
    $path = [IO.Path]::GetFullPath($path)
    if (-not $path.StartsWith($fixtureRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Backup fixture escaped test root'
    }
    New-Item -ItemType Directory -Path (Join-Path $path 'faces') -Force | Out-Null
    foreach ($file in @('hic_backend.dump', 'hic_isapi.dump')) {
        [System.IO.File]::WriteAllText((Join-Path $path $file), 'PGDMP-mocked-dump')
    }
    # This is the original manifest schema: old backups must remain compatible.
    @{
        createdAt = '2026-10-09T00:00:00+04:00'
        status = 'SUCCESS'
        retentionDays = 183
        sizeBytes = 2 * [Text.Encoding]::ASCII.GetByteCount('PGDMP-mocked-dump')
        includes = @('hic_backend', 'hic_isapi', 'faces', '.env-if-present')
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $path 'manifest.json') -Encoding UTF8
    return $path
}

function Read-Status {
    param([string]$Project)
    return Get-Content -LiteralPath (Join-Path $Project 'runtime/backup-status.json') -Raw -Encoding UTF8 | ConvertFrom-Json
}

try {
    # Run the copied script without -ProjectRoot, as documented for manual use.
    # PowerShell 5.1 can evaluate parameter defaults before PSScriptRoot is set.
    $defaultProject = New-Fixture 'default-project-root'
    $defaultScripts = Join-Path $defaultProject 'scripts'
    New-Item -ItemType Directory -Path $defaultScripts | Out-Null
    $defaultScript = Join-Path $defaultScripts 'backup-attendra.ps1'
    Copy-Item -LiteralPath $backupScript -Destination $defaultScript
    & $defaultScript
    Assert-True ((Read-Status $defaultProject).status -eq 'SUCCESS') 'default project root resolves beside the script'
    Assert-True ((Get-ChildItem -LiteralPath (Join-Path $defaultProject 'backups') -Directory).Count -eq 1) 'default-root backup stays inside its fixture'

    $project = New-Fixture 'retention'
    $oldValid = New-Backup $project '20200101-120000'
    $recent = New-Backup $project '20261008-120000'
    $boundary = New-Backup $project '20260409-000000' # Exactly 183 days: keep.
    $unrelated = Join-Path $project 'backups/20200102-120000'
    New-Item -ItemType Directory -Path $unrelated | Out-Null
    Set-Content -LiteralPath (Join-Path $unrelated 'important.txt') -Value 'Unrelated data'
    $linkTarget = Join-Path $fixtureRoot 'link-target'
    New-Item -ItemType Directory -Path $linkTarget | Out-Null
    Set-Content -LiteralPath (Join-Path $linkTarget 'keep.txt') -Value 'Linked data must survive'
    $linkedBackup = New-Backup $project '20200120-120000'
    New-Item -ItemType Junction -Path (Join-Path $linkedBackup 'faces/linked') -Target $linkTarget | Out-Null
    $rootLink = Join-Path $project 'backups/20200121-120000'
    New-Item -ItemType Junction -Path $rootLink -Target $linkedBackup | Out-Null
    $invalidPaths = @()
    $index = 3
    foreach ($kind in @('empty', 'bad-json', 'failed', 'missing-dump', 'zero-dump', 'bad-header', 'size-mismatch', 'missing-faces', 'extra-file', 'incomplete')) {
        $name = '202001{0:00}-120000' -f $index
        $index++
        if ($kind -eq 'incomplete') { $name += '.incomplete' }
        $path = New-Backup $project $name
        switch ($kind) {
            'empty' { Get-ChildItem -LiteralPath $path | Remove-Item -Recurse -Force }
            'bad-json' { Set-Content -LiteralPath (Join-Path $path 'manifest.json') -Value '{bad' }
            'failed' {
                $manifest = Get-Content -LiteralPath (Join-Path $path 'manifest.json') -Raw | ConvertFrom-Json
                $manifest.status = 'ERROR'
                $manifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $path 'manifest.json')
            }
            'missing-dump' { Remove-Item -LiteralPath (Join-Path $path 'hic_isapi.dump') }
            'zero-dump' { [IO.File]::WriteAllText((Join-Path $path 'hic_backend.dump'), '') }
            'bad-header' { [IO.File]::WriteAllText((Join-Path $path 'hic_backend.dump'), 'XXXXX-mocked-dump') }
            'size-mismatch' { Add-Content -LiteralPath (Join-Path $path 'hic_backend.dump') -Value 'changed' }
            'missing-faces' { Remove-Item -LiteralPath (Join-Path $path 'faces') }
            'extra-file' { Set-Content -LiteralPath (Join-Path $path 'important.txt') -Value 'Keep' }
        }
        $invalidPaths += $path
    }
    & $backupScript -ProjectRoot $project -Force
    Assert-True ((Read-Status $project).status -eq 'SUCCESS') 'mock backup completes'
    Assert-True (-not (Test-Path -LiteralPath $oldValid)) 'confirmed old backup is removed'
    foreach ($path in @($recent, $boundary, $unrelated, $linkedBackup, $rootLink, (Join-Path $linkTarget 'keep.txt')) + $invalidPaths) {
        Assert-True (Test-Path -LiteralPath $path) "retention preserves $path"
    }

    foreach ($kind in @('empty', 'corrupt')) {
        $project = New-Fixture "today-$kind"
        $path = New-Backup $project '20261009-120000' # Also exercise timestamp collision.
        if ($kind -eq 'empty') { Get-ChildItem -LiteralPath $path | Remove-Item -Recurse -Force }
        else { Set-Content -LiteralPath (Join-Path $path 'manifest.json') -Value '{bad' }
        $global:attendraBackupTest.calls = 0
        & $backupScript -ProjectRoot $project
        Assert-True ((Read-Status $project).status -eq 'SUCCESS') "$kind today folder does not skip backup"
        Assert-True ($global:attendraBackupTest.calls -gt 0) "$kind today folder invokes mock Docker"
        Assert-True (Test-Path -LiteralPath $path) 'existing invalid folder is preserved'
        Assert-True (Test-Path -LiteralPath (Join-Path $project 'backups/20261009-120001/manifest.json')) 'collision uses a new directory'
        & $backupScript -ProjectRoot $project
        Assert-True ((Read-Status $project).status -eq 'SKIPPED') 'newly generated backup is recognized next run'
    }

    $project = New-Fixture 'today-valid'
    $null = New-Backup $project '20261009-000000'
    $global:attendraBackupTest.calls = 0
    & $backupScript -ProjectRoot $project
    Assert-True ((Read-Status $project).status -eq 'SKIPPED') 'valid today backup is skipped'
    Assert-True ($global:attendraBackupTest.calls -eq 0) 'skip does not contact Docker'
    Assert-True ([DateTimeOffset](Read-Status $project).lastBackupAt -eq [DateTimeOffset]'2026-10-09T00:00:00+04:00') 'skip uses manifest even without previous status'
    Assert-True ((Get-Content -LiteralPath (Join-Path $project 'runtime/backup-status.json') -Raw) -match '"lastBackupAt"\s*:\s*"\d{4}-\d{2}-\d{2}T') 'status timestamp stays ISO formatted'
    & $backupScript -ProjectRoot $project -Force
    Assert-True ((Read-Status $project).status -eq 'SUCCESS') 'force still creates backup'

    foreach ($mode in @('dump-failure', 'corrupt-copy')) {
        $project = New-Fixture $mode
        $oldValid = New-Backup $project '20200101-120000'
        $global:attendraBackupTest.mode = $mode
        $failed = $false
        try { & $backupScript -ProjectRoot $project -Force } catch { $failed = $true }
        Assert-True $failed "$mode still fails backup"
        Assert-True ((Read-Status $project).status -eq 'ERROR') 'failed backup reports error'
        Assert-True (Test-Path -LiteralPath $oldValid) 'failure never prunes old backup'
    }

    $global:attendraBackupTest.mode = 'ready'
    $project = New-Fixture 'unicode-path'
    $expectedRoot = Join-Path $project ('backups-' + [char]0x018F + 'razi')
    $json = @{ enabled = $true; folderPath = $expectedRoot } | ConvertTo-Json
    [IO.File]::WriteAllText((Join-Path $project 'runtime/backup-settings.json'), $json, (New-Object Text.UTF8Encoding($false)))
    [IO.File]::WriteAllText((Join-Path $project '.env'), 'TEST_FIXTURE_ONLY=true')
    & $backupScript -ProjectRoot $project
    Assert-True ((Read-Status $project).status -eq 'SUCCESS') 'UTF8 settings without BOM work'
    Assert-True (Test-Path -LiteralPath (Join-Path $expectedRoot '20261009-120000/manifest.json')) 'Azerbaijani backup path is preserved'
    Assert-True ([IO.File]::ReadAllText((Join-Path $expectedRoot '20261009-120000/.env.backup')) -eq 'TEST_FIXTURE_ONLY=true') 'fixture env copy is intact'
    $expectedMessage = 'Backup u' + [char]0x011F + 'urla tamamland' + [char]0x0131 + '.'
    Assert-True ((Read-Status $project).message -eq $expectedMessage) 'Azerbaijani source literals survive PowerShell 5.1'
    & $backupScript -ProjectRoot $project
    Assert-True ((Read-Status $project).status -eq 'SKIPPED') 'Unicode path is recognized next run'

    foreach ($mode in @('cleanup-failure', 'cleanup-exit-failure', 'cleanup-throw', 'dump-and-cleanup-failure')) {
        $project = New-Fixture $mode
        $global:attendraBackupTest.mode = $mode
        $caught = $null
        try { & $backupScript -ProjectRoot $project } catch { $caught = $_ }
        if ($mode -eq 'dump-and-cleanup-failure') {
            $expectedError = ([char]0x018F).ToString() + 'sas bazan' + [char]0x0131 + 'n backup-u al' + [char]0x0131 + 'nmad' + [char]0x0131 + '.'
            Assert-True ($null -ne $caught -and $caught.Exception.Message -eq $expectedError) 'cleanup never masks the original dump failure'
            Assert-True ((Read-Status $project).status -eq 'ERROR') 'dump failure still reports ERROR'
        } else {
            Assert-True ($null -eq $caught) "$mode does not throw after a completed backup"
            Assert-True ((Read-Status $project).status -eq 'SUCCESS') "$mode preserves SUCCESS"
            Assert-True ((Read-Status $project).message -like '*dump*') 'cleanup warning is visible in status'
        }
    }

    $project = New-Fixture 'date-on-error'
    $statusPath = Join-Path $project 'runtime/backup-status.json'
    $previousInstant = '2026-10-08T16:40:00+04:00'
    @{ status = 'SUCCESS'; lastBackupAt = $previousInstant; lastBackupBytes = 12; totalBackupBytes = 24 } |
        ConvertTo-Json | Set-Content -LiteralPath $statusPath -Encoding UTF8
    $global:attendraBackupTest.mode = 'dump-failure'
    try { & $backupScript -ProjectRoot $project } catch { }
    $rawStatus = [IO.File]::ReadAllText($statusPath)
    Assert-True ($rawStatus -match '"lastBackupAt"\s*:\s*"\d{4}-\d{2}-\d{2}T[^" ]+(Z|[+-]\d{2}:\d{2})"') 'error status keeps ISO timestamp with timezone'
    Assert-True ([DateTimeOffset](Read-Status $project).lastBackupAt -eq [DateTimeOffset]$previousInstant) 'error status preserves previous instant'
    Assert-True ((Read-Status $project).lastBackupBytes -eq 12) 'error status preserves previous backup size'

    # Load only function declarations to test readiness without executing the script body.
    $tokens = $null; $parseErrors = $null
    $ast = [System.Management.Automation.Language.Parser]::ParseFile($backupScript, [ref]$tokens, [ref]$parseErrors)
    Assert-True ($parseErrors.Count -eq 0) 'production script parses'
    foreach ($definition in $ast.FindAll({ param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] }, $false)) {
        Invoke-Expression $definition.Extent.Text
    }
    Assert-True ($null -eq (Get-CompletedBackup -Path $oldValid -BackupRoot (Join-Path $fixtureRoot 'different-root'))) 'backup outside selected root is rejected'
    $global:attendraBackupTest.mode = 'recover'
    $global:attendraBackupTest.sleeps = 0
    Wait-AttendraContainers
    Assert-True ($global:attendraBackupTest.sleeps -eq 1) 'native stderr retries then recovers'
    Assert-True ($ErrorActionPreference -eq 'Stop') 'probe does not relax caller error handling'
    foreach ($mode in @('offline', 'empty', 'exit-failure')) {
        $global:attendraBackupTest.mode = $mode
        $global:attendraBackupTest.sleeps = 0
        $failed = $false
        try { Wait-AttendraContainers } catch { $failed = $true }
        Assert-True $failed "$mode times out"
        Assert-True ($global:attendraBackupTest.sleeps -eq 20) "$mode waits the full 10 minutes (mock clock)"
    }
    Write-Host "PASS: $script:assertions assertions; PowerShell $($PSVersionTable.PSVersion). No real Docker/device calls."
} finally {
    $resolvedFixture = [IO.Path]::GetFullPath($fixtureRoot)
    $allowedParent = [IO.Path]::GetFullPath((Join-Path $projectRoot 'build'))
    if ([IO.Path]::GetDirectoryName($resolvedFixture) -ne $allowedParent -or
        [IO.Path]::GetFileName($resolvedFixture) -notlike 'backup-tests-*') { throw 'Unsafe fixture cleanup path' }
    Remove-Item -LiteralPath $resolvedFixture -Recurse -Force
    Remove-Variable -Name attendraBackupTest -Scope Global
}
