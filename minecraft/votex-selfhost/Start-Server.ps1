[CmdletBinding()]
param([string]$JavaPath='java',[ValidateRange(2,32)][int]$MemoryGiB=4)
$ErrorActionPreference='Stop'
$javaVersionText=(& $JavaPath -version 2>&1 | Out-String)
if($javaVersionText -notmatch '(?:version|openjdk)\s+"?(\d+)' -or [int]$Matches[1] -lt 25){throw 'Java 25 oder neuer wird benötigt. Bitte -JavaPath auf die passende Java-Installation setzen.'}
if(-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'fabric-server-launch.jar'))){throw 'Fabric-Server fehlt. Zuerst Prepare-Server.ps1 ausführen.'}
$eula=Join-Path $PSScriptRoot 'eula.txt'
if(-not (Test-Path -LiteralPath $eula) -or ([IO.File]::ReadAllText($eula) -notmatch '(?m)^\s*eula\s*=\s*true\s*$')){throw 'Bitte die Minecraft-EULA lesen und bei Zustimmung in eula.txt eula=true setzen: https://www.minecraft.net/eula'}
Push-Location -LiteralPath $PSScriptRoot
try { & $JavaPath '-Xms512M' ('-Xmx'+$MemoryGiB+'G') '-jar' 'fabric-server-launch.jar' 'nogui'; if($LASTEXITCODE -ne 0){throw ('Minecraft-Server beendet mit Code '+$LASTEXITCODE)} }
finally { Pop-Location }
