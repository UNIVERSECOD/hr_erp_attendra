Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Write-Utf8([string]$Path, [string]$Text) {
    [IO.File]::WriteAllText($Path, $Text, (New-Object Text.UTF8Encoding($false)))
}
function New-Secret([int]$Bytes = 32) {
    $buffer = New-Object byte[] $Bytes
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($buffer) } finally { $rng.Dispose() }
    return ([BitConverter]::ToString($buffer)).Replace('-', '').ToLowerInvariant()
}
function Assert-FreePorts([int[]]$Ports) {
    foreach ($port in $Ports) {
        $listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Any, $port)
        try { $listener.Start() } catch { throw "Port $port is already in use. Stop the conflicting application before installation." }
        finally { $listener.Stop() }
    }
}
function Initialize-Native([string]$InstallRoot, [string]$DataRoot, [int]$WebPort = 3000,
                           [int]$DatabasePort = 15432, [int]$BackendPort = 18080, [int]$IsapiPort = 18081) {
    $configFile = Join-Path $DataRoot 'native.json'
    if (Test-Path -LiteralPath $configFile) {
        $existing = Get-Content -LiteralPath $configFile -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($existing.installRoot -ne $InstallRoot) { throw 'Existing installation belongs to another directory.' }
        return $existing
    }
    if (Test-Path -LiteralPath (Join-Path $DataRoot 'postgres')) { throw 'Unrecognized database directory. Refusing to initialize over existing data.' }
    Assert-FreePorts @($WebPort, $DatabasePort, $BackendPort, $IsapiPort)
    New-Item -ItemType Directory -Force $DataRoot | Out-Null
    $ownerSid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    & icacls.exe $DataRoot /inheritance:r /grant:r '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-19:(OI)(CI)M' "*$($ownerSid):(OI)(CI)F" | Out-Null
    if ($LASTEXITCODE) { throw 'Cannot protect application data directory.' }
    foreach ($name in @('runtime', 'faces', 'logs', 'backups')) { New-Item -ItemType Directory -Force (Join-Path $DataRoot $name) | Out-Null }
    $config = [ordered]@{ format = 1; installRoot = $InstallRoot; dataRoot = $DataRoot;
        webPort = $WebPort; databasePort = $DatabasePort; backendPort = $BackendPort; isapiPort = $IsapiPort;
        databasePassword = New-Secret; jwtSecret = New-Secret 48; encryptionKey = New-Secret 16; apiKey = New-Secret }
    # Persist secrets before initialization: a failed install is repairable without rotating keys.
    Write-Utf8 $configFile ($config | ConvertTo-Json)
    Write-Utf8 (Join-Path $DataRoot 'runtime\backup-settings.json') (@{
        enabled = $true; folderPath = (Join-Path $DataRoot 'backups'); retentionDays = 183 } | ConvertTo-Json)
    return [pscustomobject]$config
}
function Initialize-Database($Config) {
    $pgData = Join-Path $Config.dataRoot 'postgres'
    if (Test-Path -LiteralPath (Join-Path $pgData 'PG_VERSION')) {
        if ((Get-Content (Join-Path $pgData 'PG_VERSION')).Trim() -ne '15') { throw 'Unsupported PostgreSQL major version; data migration required.' }
        return
    }
    if ((Test-Path -LiteralPath $pgData) -and @(Get-ChildItem -LiteralPath $pgData -Force).Count) {
        throw 'Incomplete database initialization; preserve this directory and investigate before retrying.'
    }
    $passwordFile = Join-Path $Config.dataRoot 'initdb-password.tmp'
    try {
        Write-Utf8 $passwordFile $Config.databasePassword
        & (Join-Path $Config.installRoot 'postgres\bin\initdb.exe') -D $pgData -U hic_user --encoding=UTF8 --locale=C --auth=scram-sha-256 "--pwfile=$passwordFile" *> (Join-Path $Config.dataRoot 'logs\initdb.log')
        if ($LASTEXITCODE) { throw 'Database initialization failed. See logs/initdb.log.' }
    } finally { Remove-Item -LiteralPath $passwordFile -Force -ErrorAction SilentlyContinue }
    Write-Utf8 (Join-Path $pgData 'postgresql.auto.conf') "listen_addresses = '127.0.0.1'`nport = $($Config.databasePort)`ntimezone = 'Asia/Baku'`n"
}
function Wait-Database($Config) {
    $deadline = (Get-Date).AddSeconds(60)
    do {
        & (Join-Path $Config.installRoot 'postgres\bin\pg_isready.exe') -h 127.0.0.1 -p $Config.databasePort -U hic_user *> $null
        if ($LASTEXITCODE -eq 0) { return }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw 'PostgreSQL readiness timed out.'
}
function Ensure-Databases($Config) {
    $prior = $env:PGPASSWORD
    try {
        $env:PGPASSWORD = $Config.databasePassword
        $psql = Join-Path $Config.installRoot 'postgres\bin\psql.exe'
        foreach ($db in @('hic_backend', 'hic_isapi')) {
            $exists = & $psql -h 127.0.0.1 -p $Config.databasePort -U hic_user -d postgres -Atc "select 1 from pg_database where datname='$db'"
            if ($LASTEXITCODE) { throw 'Database authentication/readiness check failed.' }
            if ($exists -ne '1') {
                & $psql -h 127.0.0.1 -p $Config.databasePort -U hic_user -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE $db" | Out-Null
                if ($LASTEXITCODE) { throw "Cannot create $db database." }
            }
        }
    } finally { $env:PGPASSWORD = $prior }
}
function Write-ServiceFiles($Config) {
    $app = $Config.installRoot; $data = $Config.dataRoot
    $services = Join-Path $app 'services'
    New-Item -ItemType Directory -Force $services | Out-Null
    $definitions = @{
        Database = @{ exe = "$app\postgres\bin\postgres.exe"; args = "-D `"$data\postgres`""; dependency = '' }
        Isapi = @{ exe = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"; args = "-NoProfile -ExecutionPolicy Bypass -File `"$app\scripts\Start-Component.ps1`" -Component Isapi -DataRoot `"$data`""; dependency = 'AttendraDatabase' }
        Backend = @{ exe = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"; args = "-NoProfile -ExecutionPolicy Bypass -File `"$app\scripts\Start-Component.ps1`" -Component Backend -DataRoot `"$data`""; dependency = 'AttendraIsapi' }
        Web = @{ exe = "$app\web\caddy.exe"; args = "run --config `"$data\Caddyfile`" --adapter caddyfile"; dependency = 'AttendraBackend' }
    }
    foreach ($name in $definitions.Keys) {
        $def = $definitions[$name]
        $escape = { param($text) [Security.SecurityElement]::Escape($text) }
        $dependency = if ($def.dependency) { "<depend>$($def.dependency)</depend>" } else { '' }
        $xml = @"
<service>
 <id>Attendra$name</id><name>Attendra $name</name><description>Attendra attendance system</description>
 <executable>$(& $escape $def.exe)</executable><arguments>$(& $escape $def.args)</arguments>
 <workingdirectory>$(& $escape $data)</workingdirectory>
 <serviceaccount><domain>NT AUTHORITY</domain><user>LocalService</user></serviceaccount>
 <startmode>Automatic</startmode><delayedAutoStart>true</delayedAutoStart>$dependency
 <onfailure action="restart" delay="15 sec"/><stoptimeout>45 sec</stoptimeout>
 <logpath>$(& $escape "$data\logs")</logpath><log mode="roll-by-size"><sizeThreshold>10240</sizeThreshold><keepFiles>5</keepFiles></log>
</service>
"@
        Write-Utf8 "$services\Attendra$name.xml" $xml
        Copy-Item -LiteralPath "$app\tools\WinSW.exe" -Destination "$services\Attendra$name.exe" -Force
    }
    $webRoot = "$app\web\dist".Replace('\', '/')
    Write-Utf8 "$data\Caddyfile" @"
{
 admin off
 auto_https off
}
:$($Config.webPort) {
 header {
  X-Content-Type-Options nosniff
  X-Frame-Options SAMEORIGIN
  Referrer-Policy strict-origin-when-cross-origin
  Permissions-Policy "camera=(self), microphone=(), geolocation=()"
 }
 handle /api/* {
  reverse_proxy 127.0.0.1:$($Config.backendPort)
 }
 handle {
  root * "$webRoot"
  try_files {path} /index.html
  file_server
 }
}
"@
}
function Repair-ServiceAccount([string]$Name, [string]$InstallRoot) {
    $service = Get-CimInstance Win32_Service -Filter "Name='Attendra$Name'" -ErrorAction Stop
    if (-not $service) { return }
    $expected = Join-Path $InstallRoot "services\Attendra$Name.exe"
    if ($service.PathName.Trim('"') -ne $expected) { throw 'Service belongs to another installation; refusing to change it.' }
    if ($service.StartName -ne 'NT AUTHORITY\LocalService') {
        if ($service.State -ne 'Stopped') { throw 'Stop the service before changing its account.' }
        $result = Invoke-CimMethod -InputObject $service -MethodName Change -Arguments @{
            StartName = 'NT AUTHORITY\LocalService'; StartPassword = ''
        }
        if ($result.ReturnValue -ne 0) { throw "Cannot repair Attendra$Name service account (code $($result.ReturnValue))." }
    }
    $actual = Get-CimInstance Win32_Service -Filter "Name='Attendra$Name'"
    if ($actual.StartName -ne 'NT AUTHORITY\LocalService') { throw 'Service account verification failed.' }
}

function Wait-Http([string]$Url, [int]$Seconds = 120, [hashtable]$Headers = @{}) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        try { if ((Invoke-WebRequest $Url -Headers $Headers -UseBasicParsing -TimeoutSec 3).StatusCode -eq 200) { return } } catch { }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Service readiness timed out: $Url"
}
