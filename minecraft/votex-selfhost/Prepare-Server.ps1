[CmdletBinding()]
param(
 [Parameter(Mandatory=$true)][string]$Destination,
 [string]$JavaPath='java',
 [string]$RelayJar=(Join-Path $PSScriptRoot '..\votex-relay\build\libs\votex-social-relay-0.4.1.jar'),
 [string]$PublicHost=''
)
$ErrorActionPreference='Stop'
$target=[IO.Path]::GetFullPath($Destination)
if($target -eq [IO.Path]::GetPathRoot($target)){throw 'Bitte einen eigenen Server-Unterordner verwenden.'}
if((Test-Path -LiteralPath $target) -and (Get-ChildItem -LiteralPath $target -Force | Select-Object -First 1)){throw 'Der Zielordner muss leer sein. Bestehende Welten werden nicht überschrieben.'}
if(-not (Test-Path -LiteralPath $RelayJar)){
 $bundledRelay=Join-Path $PSScriptRoot 'mods\votex-social-relay-0.4.1.jar'
 if(Test-Path -LiteralPath $bundledRelay){$RelayJar=$bundledRelay}else{throw 'Relay-JAR fehlt. Zuerst den Fabric-Begleiter bauen oder das vollständige Self-Host-Paket verwenden.'}
}
if($PublicHost -and $PublicHost -notmatch '^[A-Za-z0-9.-]{1,253}$'){throw 'PublicHost muss ein Hostname oder eine IPv4-Adresse ohne Port sein.'}
$javaVersionText=(& $JavaPath -version 2>&1 | Out-String)
if($javaVersionText -notmatch '(?:version|openjdk)\s+"?(\d+)' -or [int]$Matches[1] -lt 25){throw 'Java 25 oder neuer wird benötigt.'}
New-Item -ItemType Directory -Path $target -Force | Out-Null
$downloads=New-Item -ItemType Directory -Path (Join-Path $target '.downloads') -Force
function Get-VerifiedFile([string]$Url,[string]$Path,[string]$Algorithm,[string]$Expected){
 $uri=[Uri]$Url
 if($uri.Scheme -ne 'https' -or $uri.Host -notin @('maven.fabricmc.net','cdn.modrinth.com')){throw 'Downloadquelle nicht erlaubt.'}
 Invoke-WebRequest -Uri $uri -OutFile $Path -MaximumRedirection 5
 $actual=(Get-FileHash -LiteralPath $Path -Algorithm $Algorithm).Hash.ToLowerInvariant()
 if($actual -ne $Expected){Remove-Item -LiteralPath $Path;throw ('Prüfsumme stimmt nicht: '+[IO.Path]::GetFileName($Path))}
}
$installer=Join-Path $downloads.FullName 'fabric-installer-1.1.2.jar'
Get-VerifiedFile 'https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.1.2/fabric-installer-1.1.2.jar' $installer 'SHA256' '61e035bf7bf70153e127440ce34de47c9036f0a2d0c65d1529454bd35ceefe4f'
& $JavaPath -jar $installer server -mcversion 26.2 -loader 0.19.5 -downloadMinecraft -dir $target
if($LASTEXITCODE -ne 0){throw 'Fabric-Servervorbereitung fehlgeschlagen.'}
$mods=New-Item -ItemType Directory -Path (Join-Path $target 'mods') -Force
Copy-Item -LiteralPath $RelayJar -Destination (Join-Path $mods.FullName 'votex-social-relay-0.4.1.jar')
Get-VerifiedFile 'https://cdn.modrinth.com/data/P7dR8mSH/versions/ewUK83HI/fabric-api-0.161.0%2B26.2.jar' (Join-Path $mods.FullName 'fabric-api-0.161.0+26.2.jar') 'SHA512' '2502fa5ade78e9a120b3747bc1a9a2167d6671743bea45bf115d3174c94121aeda87c549446d1a604a9c11fb828de17a551e3514ce42f3e93069dd14bdaee55b'
Get-VerifiedFile 'https://cdn.modrinth.com/data/9eGKb6K1/versions/Ls232EsW/voicechat-fabric-2.6.24%2B26.2.jar' (Join-Path $mods.FullName 'voicechat-fabric-2.6.24+26.2.jar') 'SHA512' '592c66fafdf00d9b9b23f23da8dbfd8bb996c2f639b8d55c2028a9fd55d79a7df67f1d23e1ef11a52919baa6b946a65190f805f884f0d50492db10d8bcd83db0'
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'server.properties.example') -Destination (Join-Path $target 'server.properties')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Start-Server.ps1') -Destination $target
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Start-Server.sh') -Destination $target
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'README.md') -Destination (Join-Path $target 'VOTEX-SELFHOST.md')
$voiceFolder=New-Item -ItemType Directory -Path (Join-Path $target 'config\voicechat') -Force
$voice=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'voicechat-server.properties.example')).Replace('PUBLIC_VOICE_HOST',$(if($PublicHost){$PublicHost+':24454'}else{''}))
[IO.File]::WriteAllText((Join-Path $voiceFolder.FullName 'voicechat-server.properties'),$voice,[Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $target 'whitelist.json'),'[]',[Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $target 'eula.txt'),"# Minecraft EULA: https://www.minecraft.net/eula`neula=false`n",[Text.UTF8Encoding]::new($false))
$manifest=@{schemaVersion=1;minecraft='26.2';fabricLoader='0.19.5';fabricApi='0.161.0+26.2';simpleVoiceChat='2.6.24+26.2';preparedUtc=[DateTime]::UtcNow.ToString('o');files=@(Get-ChildItem -LiteralPath $target -Recurse -File | Where-Object {$_.Extension -in @('.jar','.properties','.ps1','.sh') -and $_.FullName -notlike '*\.downloads\*'} | ForEach-Object {@{file=[IO.Path]::GetRelativePath($target,$_.FullName).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}})}
[IO.File]::WriteAllText((Join-Path $target 'votex-server-manifest.json'),($manifest | ConvertTo-Json -Depth 6),[Text.UTF8Encoding]::new($false))
Write-Output ('Server vorbereitet: '+$target)
Write-Output 'Vor dem Start eula=true setzen, wenn die Minecraft-EULA akzeptiert wurde. Danach Start-Server.ps1 ausführen und in der Serverkonsole whitelist add DEIN_MICROSOFT_SPIELERNAME verwenden.'
Write-Output 'Der Server wurde noch nicht gestartet. Es wurden keine Firewall-, Router- oder Windows-Sicherheitseinstellungen verändert.'
