[CmdletBinding()]
param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$TaskName = 'Attendra Daily Backup',
    [string]$FolderPickerTaskName = 'Attendra Backup Folder Picker'
)

$ErrorActionPreference = 'Stop'
$principal = New-Object Security.Principal.WindowsPrincipal(
    [Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Bu skripti PowerShell-i Administrator kimi açaraq işlədin.'
}

$resolvedProjectRoot = [System.IO.Path]::GetFullPath($ProjectRoot)
$backupScript = Join-Path $resolvedProjectRoot 'scripts\backup-attendra.ps1'
if (-not (Test-Path -LiteralPath $backupScript -PathType Leaf)) {
    throw "Backup skripti tapılmadı: $backupScript"
}
$folderPickerScript = Join-Path $resolvedProjectRoot 'scripts\backup-folder-picker.ps1'
if (-not (Test-Path -LiteralPath $folderPickerScript -PathType Leaf)) {
    throw "Backup qovluq seçici skripti tapılmadı: $folderPickerScript"
}

$runtimeDirectory = Join-Path $resolvedProjectRoot 'runtime'
$settingsFile = Join-Path $runtimeDirectory 'backup-settings.json'
New-Item -ItemType Directory -Force -Path $runtimeDirectory | Out-Null
if (-not (Test-Path -LiteralPath $settingsFile -PathType Leaf)) {
    $defaultSettings = [ordered]@{
        enabled = $true
        folderPath = 'C:\AttendraBackups\daily'
        retentionDays = 183
    } | ConvertTo-Json
    [System.IO.File]::WriteAllText(
        $settingsFile, $defaultSettings, (New-Object System.Text.UTF8Encoding($false)))
}

$arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$backupScript`" -ProjectRoot `"$resolvedProjectRoot`""
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $arguments
$logonTrigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$logonTrigger.Delay = 'PT3M'
$dailyTrigger = New-ScheduledTaskTrigger -Daily -At '04:00'
$taskPrincipal = New-ScheduledTaskPrincipal `
    -UserId ([Security.Principal.WindowsIdentity]::GetCurrent().Name) `
    -LogonType Interactive `
    -RunLevel Highest
$taskSettings = New-ScheduledTaskSettingsSet `
    -StartWhenAvailable `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -MultipleInstances IgnoreNew `
    -ExecutionTimeLimit (New-TimeSpan -Hours 1)

Register-ScheduledTask `
    -TaskName $TaskName `
    -Action $action `
    -Trigger @($logonTrigger, $dailyTrigger) `
    -Principal $taskPrincipal `
    -Settings $taskSettings `
    -Description 'Attendra bazaları, üz şəkilləri və konfiqurasiyası üçün gündəlik backup.' `
    -Force | Out-Null

$existingPickerTask = Get-ScheduledTask -TaskName $FolderPickerTaskName -ErrorAction SilentlyContinue
if ($existingPickerTask -and $existingPickerTask.State -eq 'Running') {
    Stop-ScheduledTask -TaskName $FolderPickerTaskName
}

$pickerArguments = "-NoProfile -STA -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$folderPickerScript`""
$pickerAction = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $pickerArguments
$pickerTrigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$pickerSettings = New-ScheduledTaskSettingsSet `
    -StartWhenAvailable `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -MultipleInstances IgnoreNew `
    -RestartCount 3 `
    -RestartInterval (New-TimeSpan -Minutes 1) `
    -ExecutionTimeLimit ([TimeSpan]::Zero)

Register-ScheduledTask `
    -TaskName $FolderPickerTaskName `
    -Action $pickerAction `
    -Trigger $pickerTrigger `
    -Principal $taskPrincipal `
    -Settings $pickerSettings `
    -Description 'Attendra Backup səhifəsi üçün lokal Windows qovluq seçicisi.' `
    -Force | Out-Null

Start-ScheduledTask -TaskName $FolderPickerTaskName

Write-Host "'$TaskName' tapşırığı quraşdırıldı. İlk backup növbəti girişdən 3 dəqiqə sonra və ya saat 04:00-da işləyəcək."
Write-Host "'$FolderPickerTaskName' tapşırığı quraşdırıldı və qovluq seçici başladıldı."
