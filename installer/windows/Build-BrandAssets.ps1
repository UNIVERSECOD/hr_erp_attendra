param([Parameter(Mandatory=$true)][string]$Source, [Parameter(Mandatory=$true)][string]$Destination)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
New-Item -ItemType Directory -Force $Destination | Out-Null
$logo = [Drawing.Image]::FromFile($Source)
function Draw-Logo($Graphics, [int]$X, [int]$Y, [int]$Size) {
    $Graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $scale = [Math]::Min($Size / $logo.Width, $Size / $logo.Height)
    $width = [int]($logo.Width * $scale); $height = [int]($logo.Height * $scale)
    $Graphics.DrawImage($logo, ($X + [int](($Size - $width) / 2)), ($Y + [int](($Size - $height) / 2)), $width, $height)
}
try {
    $frames = @()
    foreach ($size in @(16,24,32,48,64,128,256)) {
        $bitmap = New-Object Drawing.Bitmap($size, $size)
        $graphics = [Drawing.Graphics]::FromImage($bitmap)
        $memory = New-Object IO.MemoryStream
        try { Draw-Logo $graphics 0 0 $size; $bitmap.Save($memory, [Drawing.Imaging.ImageFormat]::Png); $frames += ,$memory.ToArray() }
        finally { $memory.Dispose(); $graphics.Dispose(); $bitmap.Dispose() }
    }
    $stream = [IO.File]::Create((Join-Path $Destination 'Attendra.ico'))
    $writer = New-Object IO.BinaryWriter($stream)
    try {
        $writer.Write([uint16]0); $writer.Write([uint16]1); $writer.Write([uint16]7)
        $offset = 6 + 16 * 7; $index = 0
        foreach ($size in @(16,24,32,48,64,128,256)) {
            $dimension = if ($size -eq 256) { 0 } else { $size }
            $writer.Write([byte]$dimension); $writer.Write([byte]$dimension); $writer.Write([byte]0); $writer.Write([byte]0)
            $writer.Write([uint16]1); $writer.Write([uint16]32); $writer.Write([uint32]$frames[$index].Length); $writer.Write([uint32]$offset)
            $offset += $frames[$index].Length; $index++
        }
        foreach ($frame in $frames) { $writer.Write([byte[]]$frame) }
    } finally { $writer.Dispose(); $stream.Dispose() }
    foreach ($kind in @('header','welcome')) {
        $width = if ($kind -eq 'header') { 150 } else { 164 }
        $height = if ($kind -eq 'header') { 57 } else { 314 }
        $bitmap = New-Object Drawing.Bitmap($width, $height)
        $graphics = [Drawing.Graphics]::FromImage($bitmap)
        $font = New-Object Drawing.Font('Segoe UI', $(if ($kind -eq 'header') { 13 } else { 20 }), ([Drawing.FontStyle]::Bold))
        $brush = New-Object Drawing.SolidBrush([Drawing.ColorTranslator]::FromHtml('#0b1b4d'))
        try {
            $graphics.Clear([Drawing.Color]::White)
            $graphics.TextRenderingHint = [Drawing.Text.TextRenderingHint]::AntiAliasGridFit
            if ($kind -eq 'header') { Draw-Logo $graphics 3 7 43; $graphics.DrawString('Attendra', $font, $brush, 49, 17) }
            else { Draw-Logo $graphics 18 60 128; $graphics.DrawString('Attendra', $font, $brush, 19, 200) }
            $bitmap.Save((Join-Path $Destination "$kind.bmp"), [Drawing.Imaging.ImageFormat]::Bmp)
            $bitmap.Save((Join-Path $Destination "$kind.png"), [Drawing.Imaging.ImageFormat]::Png)
        } finally { $brush.Dispose(); $font.Dispose(); $graphics.Dispose(); $bitmap.Dispose() }
    }
} finally { $logo.Dispose() }
