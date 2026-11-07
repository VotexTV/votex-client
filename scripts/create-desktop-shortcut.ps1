$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
$portable=Join-Path (Split-Path -Parent $project) 'VotexClient-0.3.0-Preview'
$exe=Join-Path $portable 'Votex Client.exe'
if(-not(Test-Path -LiteralPath $exe)){throw 'Package the portable application first.'}
$desktop=[Environment]::GetFolderPath('Desktop')
$shortcutPath=Join-Path $desktop 'Votex Client.lnk'
$oldShortcutPath=Join-Path $desktop 'Votex Client Alpha.lnk'
$shell=New-Object -ComObject WScript.Shell
$shortcut=$shell.CreateShortcut($shortcutPath)
$previousExe=Join-Path (Split-Path -Parent $project) 'VotexClient-0.2.0-Preview\Votex Client.exe'
$installedExe=Join-Path $env:LOCALAPPDATA 'Votex Client\votex-launcher.exe'
$knownInstalled=($shortcut.TargetPath -eq $installedExe) -and (Test-Path -LiteralPath $installedExe) -and ((Get-Item -LiteralPath $installedExe).VersionInfo.ProductName -eq 'Votex Client')
if((Test-Path -LiteralPath $shortcutPath) -and $shortcut.TargetPath -ne $exe -and $shortcut.TargetPath -ne $previousExe -and -not $knownInstalled){throw 'An unrelated desktop shortcut already exists. It has been preserved.'}
$shortcut.TargetPath=$exe
$shortcut.WorkingDirectory=$portable
$shortcutIcon=Join-Path $portable 'resources\votex-cloud.ico'
if(-not(Test-Path -LiteralPath $shortcutIcon)){throw 'The packaged Votex cloud icon is missing.'}
$shortcut.IconLocation="$shortcutIcon,0"
$shortcut.Description='Votex Client 0.3.0 Preview'
$shortcut.Save()
if(Test-Path -LiteralPath $oldShortcutPath){
    $oldShortcut=$shell.CreateShortcut($oldShortcutPath)
    if($oldShortcut.TargetPath -eq $previousExe -or $oldShortcut.TargetPath -eq $exe){Remove-Item -LiteralPath $oldShortcutPath}
}
Write-Output "Desktop shortcut created: $shortcutPath"
