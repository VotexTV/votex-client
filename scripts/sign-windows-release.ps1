param(
    [Parameter(Mandatory)] [string[]] $Files,
    [string] $CertificateThumbprint = $env:VOTEX_SIGNING_CERT_SHA1,
    [string] $TimestampUrl = $env:VOTEX_SIGNING_TIMESTAMP_URL,
    [string] $SignTool = $env:VOTEX_SIGNTOOL
)

$ErrorActionPreference = 'Stop'
$thumbprint = ($CertificateThumbprint -replace '\s', '').ToUpperInvariant()
if ($thumbprint -notmatch '^[0-9A-F]{40}$') {
    throw 'Configure VOTEX_SIGNING_CERT_SHA1 with a trusted publisher certificate before building a release.'
}
$certificate = Get-Item -LiteralPath "Cert:/CurrentUser/My/$thumbprint" -ErrorAction SilentlyContinue
$now = Get-Date
if (-not $certificate -or -not $certificate.HasPrivateKey -or
    $certificate.Subject -eq $certificate.Issuer -or
    $certificate.NotBefore -gt $now -or $certificate.NotAfter -le $now -or
    ($certificate.EnhancedKeyUsageList.ObjectId -notcontains '1.3.6.1.5.5.7.3.3')) {
    throw 'The publisher certificate must be current, CA-issued and usable for code signing.'
}
$timestamp = $null
if (-not [Uri]::TryCreate($TimestampUrl, [UriKind]::Absolute, [ref]$timestamp) -or
    $timestamp.Scheme -ne 'https') {
    throw 'Configure VOTEX_SIGNING_TIMESTAMP_URL with the HTTPS RFC 3161 endpoint from the signing provider.'
}
if (-not $SignTool) { $SignTool = (Get-Command signtool.exe -ErrorAction Stop).Source }
$tool = (Resolve-Path -LiteralPath $SignTool).Path
$paths = @($Files | ForEach-Object {
    $file = Get-Item -LiteralPath $_
    if ($file.PSIsContainer -or $file.Extension -notin '.exe', '.dll') {
        throw "Expected a Windows executable or DLL: $_"
    }
    $file.FullName
})
foreach ($path in $paths) {
    & $tool sign /sha1 $thumbprint /fd SHA256 /tr $timestamp.AbsoluteUri /td SHA256 $path
    if ($LASTEXITCODE -ne 0) { throw "Signing failed: $path" }
    & $tool verify /pa /tw $path
    if ($LASTEXITCODE -ne 0) { throw "SignTool verification failed: $path" }
    $signature = Get-AuthenticodeSignature -LiteralPath $path
    if ($signature.Status -ne 'Valid' -or
        $signature.SignerCertificate.Thumbprint -ne $thumbprint -or
        $null -eq $signature.TimeStamperCertificate) {
        throw "Publisher or timestamp verification failed: $path"
    }
}
