param(
    [Parameter(Mandatory)] [string] $Engine,
    [Parameter(Mandatory)] [string] $EngineSource,
    [Parameter(Mandatory)] [string] $Output,
    [switch] $Installer
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
$enginePath = (Resolve-Path -LiteralPath $Engine).Path
$licensePath = (Resolve-Path -LiteralPath (Join-Path $EngineSource 'LICENSE')).Path
$outputPath = [IO.Path]::GetFullPath($Output)
if (Test-Path -LiteralPath $outputPath) {
    throw 'Choose a new preview output directory.'
}

$manifest = Join-Path $root 'apps/launcher/src-tauri/Cargo.toml'
$target = if ($env:CARGO_TARGET_DIR) {
    [IO.Path]::GetFullPath($env:CARGO_TARGET_DIR)
} else {
    Join-Path $root 'apps/launcher/src-tauri/target'
}
$previousHash = $env:VOTEX_ENGINE_PREVIEW_SHA256
$previousTarget = $env:CARGO_TARGET_DIR
New-Item -ItemType Directory -Path $outputPath | Out-Null
$stagedEngine = Join-Path $outputPath 'votex-engine.exe'
Copy-Item -LiteralPath $enginePath -Destination $stagedEngine
$sign = Join-Path $PSScriptRoot 'sign-windows-release.ps1'
if ($Installer) {
    & $sign -Files @($stagedEngine)
}
Push-Location $root
try {
    $env:VOTEX_ENGINE_PREVIEW_SHA256 = (Get-FileHash -LiteralPath $stagedEngine -Algorithm SHA256).Hash
    $env:CARGO_TARGET_DIR = $target
    pnpm build
    if ($LASTEXITCODE) { throw 'Launcher frontend build failed.' }
    cargo build --release --locked --manifest-path $manifest --features tauri/custom-protocol
    if ($LASTEXITCODE) { throw 'Launcher build failed.' }

    if ((Get-FileHash -LiteralPath $stagedEngine -Algorithm SHA256).Hash -ne $env:VOTEX_ENGINE_PREVIEW_SHA256) {
        throw 'The staged engine does not match the launcher build.'
    }
    $launcher = Join-Path $target 'release/votex-launcher.exe'
    if ($Installer) {
        & $sign -Files @($launcher)
    }
    Copy-Item -LiteralPath $launcher -Destination $outputPath
    Copy-Item -LiteralPath (Join-Path $root 'apps/launcher/src-tauri/resources') -Destination $outputPath -Recurse
    Copy-Item -LiteralPath $licensePath -Destination (Join-Path $outputPath 'HORIZON-LICENSE.txt')
    Copy-Item -LiteralPath (Join-Path $root 'THIRD-PARTY-NOTICES.md') -Destination $outputPath
    if ($Installer) {
        $resources = @{
            $stagedEngine = 'votex-engine.exe'
            $licensePath = 'HORIZON-LICENSE.txt'
        }
        $resources[(Join-Path $root 'apps/launcher/src-tauri/resources/votex-client-core-0.4.1.jar')] = 'resources/votex-client-core-0.4.1.jar'
        $resources[(Join-Path $root 'THIRD-PARTY-NOTICES.md')] = 'THIRD-PARTY-NOTICES.md'
        $bundleConfig = Join-Path $outputPath 'tauri.bundle.local.json'
        @{ bundle = @{ resources = $resources } } |
            ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $bundleConfig -Encoding utf8
        Push-Location (Join-Path $root 'apps/launcher')
        try {
            $bundleStarted = [DateTime]::UtcNow
            pnpm exec tauri bundle --bundles nsis --config $bundleConfig
            if ($LASTEXITCODE) { throw 'NSIS packaging failed.' }
            $installers = @(Get-ChildItem -LiteralPath (Join-Path $target 'release/bundle/nsis') -Filter '*-setup.exe' -File |
                Where-Object LastWriteTimeUtc -ge $bundleStarted)
            if ($installers.Count -ne 1) { throw 'Expected exactly one fresh NSIS installer.' }
            & $sign -Files @($installers[0].FullName)
            Copy-Item -LiteralPath $installers[0].FullName -Destination $outputPath
        } finally {
            Pop-Location
        }
    }
} finally {
    Pop-Location
    $env:VOTEX_ENGINE_PREVIEW_SHA256 = $previousHash
    $env:CARGO_TARGET_DIR = $previousTarget
}
