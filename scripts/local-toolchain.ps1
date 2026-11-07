# Optional: use the workspace-local tools prepared during this development run.
# Standard installations can use the normal Visual Studio Developer PowerShell instead.
$ErrorActionPreference = 'Stop'
$tools = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..\work\tools'))
if (-not (Test-Path -LiteralPath (Join-Path $tools 'msvc\setup_x64.bat'))) { throw 'Workspace-local tools not found. Use Visual Studio C++ Build Tools and Rust stable MSVC.' }
$env:CARGO_HOME = Join-Path $tools 'cargo'
$env:RUSTUP_HOME = Join-Path $tools 'rustup'
$toolRoot = Join-Path $tools 'msvc'
$vc = (Get-ChildItem -LiteralPath (Join-Path $toolRoot 'VC\Tools\MSVC') -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
$sdk = Join-Path $toolRoot 'Windows Kits\10'
$sdkVersion = (Get-ChildItem -LiteralPath (Join-Path $sdk 'bin') -Directory | Where-Object Name -Match '^10\.' | Sort-Object Name -Descending | Select-Object -First 1).Name
$env:PATH = "$vc\bin\Hostx64\x64;$sdk\bin\$sdkVersion\x64;$env:CARGO_HOME\bin;$env:PATH"
$env:LIB = "$vc\lib\x64;$sdk\Lib\$sdkVersion\ucrt\x64;$sdk\Lib\$sdkVersion\um\x64"
$env:INCLUDE = "$vc\include;$sdk\Include\$sdkVersion\ucrt;$sdk\Include\$sdkVersion\shared;$sdk\Include\$sdkVersion\um;$sdk\Include\$sdkVersion\winrt"
$env:VCToolsInstallDir = "$vc\"
$env:WindowsSdkDir = "$sdk\"
$env:WindowsSDKVersion = "$sdkVersion\"
$env:RC_PATH = Join-Path $sdk "bin\$sdkVersion\x64\rc.exe"
$env:VSCMD_ARG_HOST_ARCH='x64'
$env:VSCMD_ARG_TGT_ARCH='x64'
Write-Output 'Workspace-local Rust, MSVC and Windows SDK activated for this PowerShell session.'
