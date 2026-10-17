# Fetches libmpv-2.dll, the playback engine, into windows/libmpv. It is not kept in the
# repository: the file is large and comes from its own project. The build is pinned by name
# and by SHA-256, so every machine plays with the same engine; to move to a newer one, change
# both lines below and run the script again.
#
#   pwsh windows/tools/fetch-libmpv.ps1

$ErrorActionPreference = 'Stop'

$name = 'mpv-dev-x86_64-20261017-git-dde0671072.7z'
$url = "https://github.com/shinchiro/mpv-winbuild-cmake/releases/download/20261017/$name"
$sha256 = 'd6638b2b40561e6071627f39ad60a38e15721dc87a49f244b897911ea2edfd9f'

$target = Join-Path $PSScriptRoot '..\libmpv'
$archive = Join-Path ([System.IO.Path]::GetTempPath()) $name

if (Test-Path (Join-Path $target 'libmpv-2.dll')) {
    $stamp = Join-Path $target 'source.txt'
    if ((Test-Path $stamp) -and ((Get-Content $stamp -Raw).Trim() -eq $sha256)) {
        Write-Host 'libmpv is already here.'
        exit 0
    }
}

Write-Host "Downloading $name"
Invoke-WebRequest -Uri $url -OutFile $archive
$actual = (Get-FileHash $archive -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actual -ne $sha256) {
    Remove-Item $archive
    throw "SHA-256 mismatch: expected $sha256, got $actual"
}

New-Item -ItemType Directory -Force $target | Out-Null
# The tar that ships with Windows reads 7z.
& "$env:SystemRoot\System32\tar.exe" -xf $archive -C $target
if ($LASTEXITCODE -ne 0) { throw 'Could not unpack the archive.' }
Set-Content (Join-Path $target 'source.txt') $sha256
Remove-Item $archive
Write-Host "libmpv is in $((Resolve-Path $target).Path)"
