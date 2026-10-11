param([string]$Destination = (Join-Path $PSScriptRoot '..\..\build\windows-downloads'))
$ErrorActionPreference = 'Stop'
$Destination = [IO.Path]::GetFullPath($Destination)
New-Item -ItemType Directory -Force $Destination | Out-Null
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$dependencies = Get-Content "$PSScriptRoot\dependencies.json" -Raw | ConvertFrom-Json
foreach ($dependency in $dependencies) {
    $path = Join-Path $Destination $dependency.file
    if (-not (Test-Path -LiteralPath $path)) {
        Invoke-WebRequest $dependency.url -UseBasicParsing -OutFile $path
        if ($dependency.file -eq 'nsis-final.zip' -and (Get-FileHash $path).Hash -ne $dependency.sha256) {
            $html = Get-Content $path -Raw
            $match = [regex]::Match($html, 'http-equiv="refresh" content="5; url=([^"]+)')
            $redirect = [Net.WebUtility]::HtmlDecode($match.Groups[1].Value)
            if ($redirect.StartsWith('https://downloads.sourceforge.net/')) { Invoke-WebRequest $redirect -UseBasicParsing -OutFile $path }
        }
    }
    if ((Get-FileHash -LiteralPath $path).Hash -ne $dependency.sha256) {
        throw "SHA256 mismatch: $($dependency.file). Do not silently accept changed upstream binaries."
    }
}
if ((Get-AuthenticodeSignature "$Destination\vc_redist.x64.exe").Status -ne 'Valid') { throw 'Microsoft runtime signature is not valid.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($package in @(@('postgres.zip','postgres'), @('caddy.zip','caddy'), @('nsis-final.zip','nsis'))) {
    $archive = [IO.Compression.ZipFile]::OpenRead((Join-Path $Destination $package[0]))
    $target = Join-Path $Destination $package[1]
    try {
        foreach ($entry in $archive.Entries) {
            if (-not $entry.Name) { continue }
            $relative = $entry.FullName
            if ($package[1] -eq 'postgres') {
                if ($relative -notmatch '^pgsql/(bin/|lib/|share/|[^/]+\.txt$)') { continue }
                $relative = $relative.Substring(6)
            }
            $path = [IO.Path]::GetFullPath((Join-Path $target $relative))
            if (-not $path.StartsWith($target + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe archive path.' }
            New-Item -ItemType Directory -Force (Split-Path $path -Parent) | Out-Null
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $path, $true)
        }
    } finally { $archive.Dispose() }
}
Invoke-WebRequest https://raw.githubusercontent.com/winsw/winsw/v2.12.0/LICENSE.txt -UseBasicParsing -OutFile "$Destination\WinSW-LICENSE.txt"
Write-Output "Dependencies verified: $Destination"
