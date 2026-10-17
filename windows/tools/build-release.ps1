# Builds a Windows release: the installer, its signature and its SHA-256, in windows/dist.
# Nothing is published; the last lines it prints say how to.
#
#   pwsh windows/tools/build-release.ps1 -Version 1.0.0
#
# Built from `git archive` of the commit in a folder with no one's name in its path, so no
# personal path ends up inside the files, and the files are searched for such paths before the
# installer is made. Needs: the .NET 8 SDK, Inno Setup 6, libmpv fetched by fetch-libmpv.ps1, and
# the signing key (tools/sign-update).

param(
    [Parameter(Mandatory)][ValidatePattern('^\d+\.\d+\.\d+$')][string]$Version,
    # A tree with uncommitted changes is built from its last commit; this goes on anyway.
    [switch]$AllowDirty
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$dist = Join-Path $repo 'windows\dist'

function Step($text) { Write-Host "== $text" -ForegroundColor Cyan }

$iscc = @("$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe", "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe", "$env:ProgramFiles\Inno Setup 6\ISCC.exe") |
    Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $iscc) { throw 'Inno Setup 6 is not installed (winget install JRSoftware.InnoSetup --scope user)' }

$libmpv = Join-Path $repo 'windows\libmpv'
if (-not (Test-Path (Join-Path $libmpv 'libmpv-2.dll'))) { throw 'libmpv is missing: run windows/tools/fetch-libmpv.ps1' }

Push-Location $repo
try {
    if ((git status --porcelain) -and -not $AllowDirty) { throw 'The working tree has uncommitted changes; commit them, or pass -AllowDirty to build the last commit.' }
    $commit = (git rev-parse --short HEAD).Trim()
} finally { Pop-Location }

# A short folder at the root of the drive: the user's name is in every path under their profile.
$work = Join-Path $env:SystemDrive ('\nb' + [System.Guid]::NewGuid().ToString('N').Substring(0, 6))
New-Item -ItemType Directory $work | Out-Null
try {
    Step "Source of $commit into $work"
    $tar = Join-Path $work 'source.tar'
    git -C $repo archive --format=tar -o $tar HEAD
    $src = Join-Path $work 's'
    New-Item -ItemType Directory $src | Out-Null
    tar -xf $tar -C $src
    Remove-Item $tar
    Copy-Item $libmpv (Join-Path $src 'windows\libmpv') -Recurse

    Step "Build $Version"
    $app = Join-Path $src 'windows\src\Nama.App'
    dotnet build $app -c Release -p:Platform=x64 -p:Version=$Version -p:SourceRevisionId=$commit `
        -p:DebugType=none -p:DebugSymbols=false -p:Deterministic=true -p:ContinuousIntegrationBuild=true -nologo -v:minimal
    if ($LASTEXITCODE) { throw 'build failed' }
    $out = Join-Path $app 'bin\x64\Release\net8.0-windows10.0.19041.0\win-x64'
    # Only what runs: no debug symbols, no build records.
    Get-ChildItem $out -Recurse -Include *.pdb | Remove-Item

    Step 'Search for personal names and paths'
    $words = @($env:USERNAME, $env:USERPROFILE, $repo, (git -C $repo config user.email), (git -C $repo config user.name)) |
        Where-Object { $_ -and $_.Length -ge 4 } | Select-Object -Unique
    $found = foreach ($file in Get-ChildItem $out -Recurse -File) {
        $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
        $texts = @([System.Text.Encoding]::ASCII.GetString($bytes), [System.Text.Encoding]::Unicode.GetString($bytes))
        foreach ($word in $words) {
            if ($texts | Where-Object { $_.IndexOf($word, [StringComparison]::OrdinalIgnoreCase) -ge 0 }) { "$($file.Name): $word" }
        }
    }
    if ($found) { $found | ForEach-Object { Write-Host $_ -ForegroundColor Red }; throw 'personal names or paths in the build' }

    Step 'Installer'
    New-Item -ItemType Directory $dist -Force | Out-Null
    & $iscc /Q "/DVersion=$Version" "/DSource=$out" "/DRepo=$src" "/DOutput=$dist" (Join-Path $src 'windows\installer\30nama.iss')
    if ($LASTEXITCODE) { throw 'installer failed' }
    $setup = Join-Path $dist "30nama-windows-$Version-setup.exe"

    Step 'Signature'
    dotnet run --project (Join-Path $src 'windows\tools\sign-update') -c Release -- sign $setup
    if ($LASTEXITCODE) { throw 'signing failed' }
} finally {
    Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
}

$hash = (Get-FileHash $setup -Algorithm SHA256).Hash.ToLowerInvariant()
"$hash  $(Split-Path $setup -Leaf)" | Set-Content (Join-Path $dist "30nama-windows-$Version-SHA256.txt")
$size = [math]::Round((Get-Item $setup).Length / 1MB, 1)
Write-Host ''
Write-Host "Built $setup ($size MB) from $commit"
Write-Host "SHA-256 $hash"
Write-Host ''
Write-Host 'To publish, not the latest release (that stays the Mac one):'
Write-Host "  gh release create win-v$Version `"$setup`" `"$setup.sig`" --latest=false --title `"30nama for Windows $Version`" --notes-file <notes>"
