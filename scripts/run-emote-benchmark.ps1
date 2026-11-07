$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
if(-not $env:JAVA_HOME){throw 'Set JAVA_HOME to JDK 25+ first.'}
$javac=Join-Path $env:JAVA_HOME 'bin\javac.exe'
$java=Join-Path $env:JAVA_HOME 'bin\java.exe'
$gson=Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1\com.google.code.gson\gson') -Recurse -Filter '*.jar' | Where-Object Name -NotLike '*sources*' | Select-Object -First 1 -ExpandProperty FullName
if(-not $gson){throw 'Build the Core once to populate its Gson dependency cache.'}
$probeRoot=Join-Path $project 'work\emote-allocation-probe'
$resources=Join-Path $project 'minecraft\votex-core\src\main\resources'
$probeSource=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'EmoteAllocationProbe.java') -Raw
foreach($variant in @('before','after')){
    $out=Join-Path $probeRoot $variant
    New-Item -ItemType Directory -Force -Path $out | Out-Null
    $code=$probeSource
    $timeline=Join-Path $project 'minecraft\votex-core\src\main\java\dev\votex\motion\EmoteTimeline.java'
    if($variant -eq 'before'){
        $timeline=Join-Path $project 'benchmarks\legacy\dev\votex\motion\EmoteTimeline.java'
        $code=$code.Replace('timeline.stepInto(1d/120)','timeline.step(1d/120)')
    }
    $probeFile=Join-Path $out 'EmoteAllocationProbe.java'
    [IO.File]::WriteAllText($probeFile,$code,[Text.UTF8Encoding]::new($false))
    & $javac --release 25 -encoding UTF-8 -cp $gson -d $out $timeline $probeFile
    if($LASTEXITCODE -ne 0){throw "Probe compilation failed: $variant"}
    $result=& $java -cp "$out;$resources;$gson" EmoteAllocationProbe
    if($LASTEXITCODE -ne 0){throw "Probe execution failed: $variant"}
    $result | ConvertFrom-Json | Out-Null
    [IO.File]::WriteAllText((Join-Path $probeRoot "$variant.json"),($result+"`n"),[Text.UTF8Encoding]::new($false))
    Write-Output "$variant`: $result"
}
Write-Output "Allocation-method microbenchmark only. Results: $probeRoot"
