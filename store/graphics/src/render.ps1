# Renders the Play Store graphics from graphics.html with headless Edge, then converts the
# TV screenshots to 24-bit JPEG (Play rejects screenshots with an alpha channel).
$ErrorActionPreference = "Stop"
$edge = "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
$src = $PSScriptRoot
$out = Split-Path $src -Parent
$profileDir = Join-Path $env:TEMP "tvtheme-edge-render"

$page = "file:///" + (($src -replace '\\', '/') -replace ' ', '%20') + "/graphics.html"
$targets = @(
    @{ g = "icon";    size = "512,512";   file = "icon-512.png" },
    @{ g = "feature"; size = "1024,500";  file = "feature-graphic-1024x500.png" },
    @{ g = "banner";  size = "1280,720";  file = "tv-banner-1280x720.png" }
)
foreach ($t in $targets) {
    # Edge logs harmless warnings to stderr, which "Stop" would treat as failure.
    $ErrorActionPreference = "Continue"
    & $edge --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 `
        "--user-data-dir=$profileDir" "--window-size=$($t.size)" "--screenshot=$out\$($t.file)" "$page`?g=$($t.g)" 2>$null | Out-Null
    $ErrorActionPreference = "Stop"
}
Start-Sleep -Seconds 1

Add-Type -AssemblyName System.Drawing
$shots = Join-Path $out "screenshots"
New-Item -ItemType Directory -Force $shots | Out-Null
$jpeg = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq "image/jpeg" }
$params = New-Object System.Drawing.Imaging.EncoderParameters 1
$params.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter ([System.Drawing.Imaging.Encoder]::Quality), 92L
$marketing = Join-Path (Split-Path $out -Parent | Split-Path -Parent) "marketing\screenshots"
$pick = @("01-home.png", "02-menu.png", "04-profiles.png", "05-pin.png", "06-quick-settings.png")
$i = 1
foreach ($name in $pick) {
    $img = [System.Drawing.Image]::FromFile((Join-Path $marketing $name))
    $flat = New-Object System.Drawing.Bitmap $img.Width, $img.Height, ([System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $gfx = [System.Drawing.Graphics]::FromImage($flat)
    $gfx.DrawImage($img, 0, 0, $img.Width, $img.Height)
    $flat.Save((Join-Path $shots ("tv-{0:D2}-{1}" -f $i, ($name -replace '^\d+-', '' -replace '\.png$', '.jpg'))), $jpeg, $params)
    $gfx.Dispose(); $flat.Dispose(); $img.Dispose()
    $i++
}
Get-ChildItem $out -Recurse -File -Include *.png, *.jpg | Select-Object FullName, Length
