. "$PSScriptRoot\Runtime.ps1"
$script:account = 'LocalSystem'
$script:changes = 0
function Get-CimInstance { param($ClassName,$Filter,$ErrorAction)
    [pscustomobject]@{ PathName='"C:\Attendra\services\AttendraDatabase.exe"'; StartName=$script:account; State='Stopped' }
}
function Invoke-CimMethod { param($InputObject,$MethodName,$Arguments)
    if ($MethodName -ne 'Change' -or $Arguments.StartName -ne 'NT AUTHORITY\LocalService') { throw 'Wrong account repair' }
    $script:account=$Arguments.StartName; $script:changes++; [pscustomobject]@{ReturnValue=0}
}
Repair-ServiceAccount Database 'C:\Attendra'
if ($script:account -ne 'NT AUTHORITY\LocalService' -or $script:changes -ne 1) { throw 'Repair failed' }
Repair-ServiceAccount Database 'C:\Attendra'
if ($script:changes -ne 1) { throw 'Repair is not idempotent' }
$rejected=$false
try { Repair-ServiceAccount Database 'D:\Other' } catch { $rejected=$true }
if (-not $rejected) { throw 'Foreign installation was changed' }
Write-Output 'PASS: account repair, idempotence, foreign service rejection.'
