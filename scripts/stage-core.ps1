$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$versionLine = Get-Content (Join-Path $project 'minecraft\votex-core\gradle.properties') | Where-Object { $_ -match '^mod_version=' }
$coreVersion = ($versionLine -split '=',2)[1].Trim()
if ($coreVersion -notmatch '^\d+\.\d+\.\d+$') { throw 'Invalid Core version' }
$jar = Join-Path $project "minecraft\votex-core\build\libs\votex-client-core-$coreVersion.jar"
if (-not (Test-Path -LiteralPath $jar)) { throw 'Build Minecraft Client Core first: cd minecraft/votex-core; ./gradlew.bat build' }
$resources = Join-Path $project 'apps\launcher\src-tauri\resources'
New-Item -ItemType Directory -Force -Path $resources | Out-Null
Copy-Item -LiteralPath $jar -Destination (Join-Path $resources "votex-client-core-$coreVersion.jar")
Write-Output 'Client Core staged for the Windows application bundle.'
