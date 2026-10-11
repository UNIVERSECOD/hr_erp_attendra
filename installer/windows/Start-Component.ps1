param([ValidateSet('Backend','Isapi')][string]$Component, [Parameter(Mandatory=$true)][string]$DataRoot)
$ErrorActionPreference = 'Stop'
$c = Get-Content -LiteralPath (Join-Path $DataRoot 'native.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$env:SPRING_PROFILES_ACTIVE = 'prod'
$env:ISAPI_API_KEY = $c.apiKey
$env:SERVER_ADDRESS = '127.0.0.1'
$env:TZ = 'Asia/Baku'
if ($Component -eq 'Backend') {
    $env:SERVER_PORT = [string]$c.backendPort
    $env:SPRING_DATASOURCE_URL = "jdbc:postgresql://127.0.0.1:$($c.databasePort)/hic_backend"
    $env:SPRING_DATASOURCE_USERNAME = 'hic_user'
    $env:SPRING_DATASOURCE_PASSWORD = $c.databasePassword
    $env:JWT_SECRET = $c.jwtSecret
    $env:ENCRYPTION_SECRET_KEY = $c.encryptionKey
    $env:ISAPI_BASE_URL = "http://127.0.0.1:$($c.isapiPort)"
    $env:APP_RUNTIME_DIR = Join-Path $DataRoot 'runtime'
    $env:FACE_IMAGES_DIR = Join-Path $DataRoot 'faces'
    $hosts = @('localhost', '127.0.0.1', [Net.Dns]::GetHostName())
    $hosts += @(Get-NetIPAddress -AddressFamily IPv4 -AddressState Preferred -ErrorAction SilentlyContinue | Select-Object -ExpandProperty IPAddress)
    $env:CORS_ALLOWED_ORIGINS = (($hosts | Select-Object -Unique | ForEach-Object { "http://$($_):$($c.webPort)" }) -join ',')
    $jar = 'backend.jar'
} else {
    $env:SERVER_PORT = [string]$c.isapiPort
    $env:DB_URL = "jdbc:postgresql://127.0.0.1:$($c.databasePort)/hic_isapi"
    $env:DB_USERNAME = 'hic_user'
    $env:DB_PASSWORD = $c.databasePassword
    $jar = 'isapi.jar'
}
& (Join-Path $c.installRoot 'java\bin\java.exe') '-Dfile.encoding=UTF-8' '-Duser.timezone=Asia/Baku' '-Xmx512m' -jar (Join-Path $c.installRoot "app\$jar")
exit $LASTEXITCODE
