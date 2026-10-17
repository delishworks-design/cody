# `fetch-bootstraps.ps1`
#
# Downloads the Termux rootfs bootstrap archives that are embedded into the APK as native
# `libtermux-bootstrap.so` libraries (see app/src/main/cpp/termux-bootstrap-zip.S).
#
# The archives are cached in <repo>/.cache/bootstraps and are git-ignored.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts/fetch-bootstraps.ps1

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$cacheDir = Join-Path $repoRoot ".cache\bootstraps"
New-Item -ItemType Directory -Force -Path $cacheDir | Out-Null

# Pin the bootstrap release so builds are reproducible.
$release = "bootstrap-2026.10.04-r1+apt.android-7"
$base = "https://github.com/termux/termux-packages/releases/download/$release"

$arches = @{
    "aarch64" = "arm64-v8a"
    "x86_64"  = "x86_64"
}

$headers = @{ "User-Agent" = "cody-build" }

foreach ($arch in $arches.Keys) {
    $file = Join-Path $cacheDir "bootstrap-$arch.zip"
    $url = "$base/bootstrap-$arch.zip"
    Write-Host "Downloading $url"
    Invoke-WebRequest -Uri $url -Headers $headers -OutFile $file -TimeoutSec 600
    # Validate the payload: a stale cache from an older/dead URL used to be a 9-byte "Not Found"
    # page, which silently produced a 17 MB APK with no embedded rootfs.
    $size = (Get-Item $file).Length
    $magic = [System.IO.File]::ReadAllBytes($file)[0..3]
    if ($size -lt 1MB -or $magic[0] -ne 0x50 -or $magic[1] -ne 0x4B) {
        Remove-Item $file -Force
        throw "Downloaded file is not a valid bootstrap archive: $url"
    }
    Write-Host ("  -> {0} ({1:N0} bytes)" -f $file, $size)
}

Write-Host "Done. Bootstraps cached in $cacheDir"
