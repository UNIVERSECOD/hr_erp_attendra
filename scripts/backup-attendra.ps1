[CmdletBinding()]
param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$resolvedProjectRoot = [System.IO.Path]::GetFullPath($ProjectRoot)
$runtimeDirectory = Join-Path $resolvedProjectRoot 'runtime'
$settingsFile = Join-Path $runtimeDirectory 'backup-settings.json'
$statusFile = Join-Path $runtimeDirectory 'backup-status.json'
$utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)

New-Item -ItemType Directory -Force -Path $runtimeDirectory | Out-Null

function Get-DirectoryBytes {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
        return [long]0
    }
    $sum = Get-ChildItem -LiteralPath $Path -File -Recurse -ErrorAction SilentlyContinue |
        Measure-Object -Property Length -Sum
    if ($null -eq $sum.Sum) {
        return [long]0
    }
    return [long]$sum.Sum
}

function Read-PreviousStatus {
    if (-not (Test-Path -LiteralPath $statusFile -PathType Leaf)) {
        return $null
    }
    try {
        return Get-Content -LiteralPath $statusFile -Raw | ConvertFrom-Json
    } catch {
        return $null
    }
}

function Write-BackupStatus {
    param(
        [Parameter(Mandatory = $true)][string]$Status,
        [Parameter(Mandatory = $true)][string]$Message,
        [AllowNull()][string]$LastBackupAt,
        [long]$LastBackupBytes = 0,
        [long]$TotalBackupBytes = 0
    )

    $payload = [ordered]@{
        status = $Status
        lastBackupAt = $LastBackupAt
        message = $Message
        lastBackupBytes = $LastBackupBytes
        totalBackupBytes = $TotalBackupBytes
        updatedAt = [DateTimeOffset]::Now.ToString('o')
    } | ConvertTo-Json
    $temporaryStatus = "$statusFile.tmp"
    [System.IO.File]::WriteAllText($temporaryStatus, $payload, $utf8WithoutBom)
    Move-Item -LiteralPath $temporaryStatus -Destination $statusFile -Force
}

function Get-SafeBackupRoot {
    param([Parameter(Mandatory = $true)][string]$ConfiguredPath)

    if (-not [System.IO.Path]::IsPathRooted($ConfiguredPath) -or $ConfiguredPath.Contains('..')) {
        throw 'Backup qovluğunun tam və təhlükəsiz Windows yolu göstərilməlidir.'
    }
    $fullPath = [System.IO.Path]::GetFullPath($ConfiguredPath).TrimEnd('\')
    $pathRoot = [System.IO.Path]::GetPathRoot($fullPath).TrimEnd('\')
    if ($fullPath -eq $pathRoot) {
        throw 'Diskin kök qovluğu backup üçün seçilə bilməz.'
    }
    return $fullPath
}

function Wait-AttendraContainers {
    $deadline = (Get-Date).AddMinutes(10)
    do {
        $postgresHealth = (& docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' hic_postgres 2>$null)
        $postgresExitCode = $LASTEXITCODE
        $backendState = (& docker inspect --format '{{.State.Status}}' hic_backend 2>$null)
        $backendExitCode = $LASTEXITCODE
        if ($postgresExitCode -eq 0 -and $backendExitCode -eq 0 -and
            $postgresHealth.Trim() -eq 'healthy' -and $backendState.Trim() -eq 'running') {
            return
        }
        Start-Sleep -Seconds 30
    } while ((Get-Date) -lt $deadline)

    throw 'Docker konteynerləri 10 dəqiqə ərzində hazır olmadı.'
}

try {
    if (-not (Test-Path -LiteralPath $settingsFile -PathType Leaf)) {
        throw 'Backup parametrləri tapılmadı. Əvvəl proqramın Backup bölməsində qovluğu yadda saxlayın.'
    }

    $configuration = Get-Content -LiteralPath $settingsFile -Raw | ConvertFrom-Json
    if (-not $configuration.enabled) {
        exit 0
    }

    $backupRoot = Get-SafeBackupRoot -ConfiguredPath ([string]$configuration.folderPath)
    New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null

    $previousStatus = Read-PreviousStatus
    $todayPrefix = (Get-Date).ToString('yyyyMMdd-')
    $todayBackup = Get-ChildItem -LiteralPath $backupRoot -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match ('^' + [regex]::Escape($todayPrefix) + '\d{6}$') } |
        Select-Object -First 1
    if ($todayBackup -and -not $Force) {
        Write-BackupStatus -Status 'SKIPPED' `
            -Message 'Bu gün üçün uğurlu backup artıq mövcuddur.' `
            -LastBackupAt $previousStatus.lastBackupAt `
            -LastBackupBytes ([long]$previousStatus.lastBackupBytes) `
            -TotalBackupBytes (Get-DirectoryBytes -Path $backupRoot)
        exit 0
    }

    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'Docker əmri tapılmadı.'
    }

    Write-BackupStatus -Status 'RUNNING' -Message 'Backup hazırlanır.' `
        -LastBackupAt $previousStatus.lastBackupAt `
        -LastBackupBytes ([long]$previousStatus.lastBackupBytes) `
        -TotalBackupBytes (Get-DirectoryBytes -Path $backupRoot)

    Wait-AttendraContainers

    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $incompleteDirectory = Join-Path $backupRoot "$timestamp.incomplete"
    $completedDirectory = Join-Path $backupRoot $timestamp
    New-Item -ItemType Directory -Force -Path $incompleteDirectory | Out-Null

    $backendTempDump = "/tmp/hic_backend-$timestamp.dump"
    $isapiTempDump = "/tmp/hic_isapi-$timestamp.dump"
    try {
        & docker exec hic_postgres pg_dump -U hic_user -d hic_backend -Fc -f $backendTempDump
        if ($LASTEXITCODE -ne 0) { throw 'Əsas bazanın backup-u alınmadı.' }
        & docker cp "hic_postgres:$backendTempDump" (Join-Path $incompleteDirectory 'hic_backend.dump')
        if ($LASTEXITCODE -ne 0) { throw 'Əsas bazanın backup faylı kopyalanmadı.' }

        & docker exec hic_postgres pg_dump -U hic_user -d hic_isapi -Fc -f $isapiTempDump
        if ($LASTEXITCODE -ne 0) { throw 'ISAPI bazasının backup-u alınmadı.' }
        & docker cp "hic_postgres:$isapiTempDump" (Join-Path $incompleteDirectory 'hic_isapi.dump')
        if ($LASTEXITCODE -ne 0) { throw 'ISAPI bazasının backup faylı kopyalanmadı.' }

        & docker cp 'hic_backend:/app/uploads/faces' (Join-Path $incompleteDirectory 'faces')
        if ($LASTEXITCODE -ne 0) { throw 'Üz şəkilləri kopyalanmadı.' }

        $environmentFile = Join-Path $resolvedProjectRoot '.env'
        if (Test-Path -LiteralPath $environmentFile -PathType Leaf) {
            Copy-Item -LiteralPath $environmentFile -Destination (Join-Path $incompleteDirectory '.env.backup')
        }

        foreach ($dumpName in @('hic_backend.dump', 'hic_isapi.dump')) {
            $dumpPath = Join-Path $incompleteDirectory $dumpName
            if (-not (Test-Path -LiteralPath $dumpPath -PathType Leaf) -or (Get-Item -LiteralPath $dumpPath).Length -le 0) {
                throw "$dumpName boşdur və backup etibarlı deyil."
            }
        }

        $backupBytes = Get-DirectoryBytes -Path $incompleteDirectory
        $manifest = [ordered]@{
            createdAt = [DateTimeOffset]::Now.ToString('o')
            status = 'SUCCESS'
            sizeBytes = $backupBytes
            retentionDays = 183
            includes = @('hic_backend', 'hic_isapi', 'faces', '.env-if-present')
        } | ConvertTo-Json
        [System.IO.File]::WriteAllText(
            (Join-Path $incompleteDirectory 'manifest.json'), $manifest, $utf8WithoutBom)
        Move-Item -LiteralPath $incompleteDirectory -Destination $completedDirectory
    } finally {
        & docker exec hic_postgres rm -f $backendTempDump $isapiTempDump 2>$null
    }

    $cutoff = (Get-Date).Date.AddDays(-183)
    $rootPrefix = $backupRoot.TrimEnd('\') + '\'
    Get-ChildItem -LiteralPath $backupRoot -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^\d{8}-\d{6}$' } |
        ForEach-Object {
            $parsedDate = [DateTime]::MinValue
            if ([DateTime]::TryParseExact(
                    $_.Name, 'yyyyMMdd-HHmmss',
                    [System.Globalization.CultureInfo]::InvariantCulture,
                    [System.Globalization.DateTimeStyles]::None,
                    [ref]$parsedDate) -and $parsedDate -lt $cutoff) {
                $candidate = [System.IO.Path]::GetFullPath($_.FullName)
                if ($candidate.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
                    Remove-Item -LiteralPath $candidate -Recurse -Force
                }
            }
        }

    Write-BackupStatus -Status 'SUCCESS' -Message 'Backup uğurla tamamlandı.' `
        -LastBackupAt ([DateTimeOffset]::Now.ToString('o')) `
        -LastBackupBytes (Get-DirectoryBytes -Path $completedDirectory) `
        -TotalBackupBytes (Get-DirectoryBytes -Path $backupRoot)
} catch {
    $previousStatus = Read-PreviousStatus
    Write-BackupStatus -Status 'ERROR' -Message $_.Exception.Message `
        -LastBackupAt $previousStatus.lastBackupAt `
        -LastBackupBytes ([long]$previousStatus.lastBackupBytes) `
        -TotalBackupBytes ([long]$previousStatus.totalBackupBytes)
    throw
}
