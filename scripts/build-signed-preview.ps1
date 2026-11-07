param(
    [ValidateSet('Inspect','Build')][string]$Mode = 'Inspect',
    [string]$CertificateThumbprint = $env:VOTEX_SIGNING_CERT_SHA1,
    [string]$TimestampUrl = $env:VOTEX_SIGNING_TIMESTAMP_URL
)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$available = @(Get-ChildItem Cert:\CurrentUser\My -ErrorAction SilentlyContinue | Where-Object {
    $_.HasPrivateKey -and $_.NotBefore -le (Get-Date) -and $_.NotAfter -gt (Get-Date) -and
    ($_.EnhancedKeyUsageList.ObjectId -contains '1.3.6.1.5.5.7.3.3')
})
if ($Mode -eq 'Inspect') {
    [pscustomobject]@{ usableCurrentUserCodeSigningCertificates = $available.Count; signingIdentityConfigured = -not [string]::IsNullOrWhiteSpace($CertificateThumbprint); changesMade = $false } | ConvertTo-Json
    return
}
# This uses an existing signing identity. It never creates certificates or changes trust/security policy.
$thumb = ($CertificateThumbprint -replace '\s','').ToUpperInvariant()
if ($thumb -notmatch '^[0-9A-F]{40}$') { throw 'Supply the SHA-1 thumbprint of an existing CA-issued code-signing certificate; no signing identity was created.' }
$cert = @($available | Where-Object { $_.Thumbprint -eq $thumb })
if ($cert.Count -ne 1) { throw 'The selected current-user certificate is missing, expired, lacks a private key or lacks code-signing usage.' }
if ($cert[0].Subject -eq $cert[0].Issuer) { throw 'Self-signed certificates do not establish Smart App Control publisher trust.' }
$chain = [Security.Cryptography.X509Certificates.X509Chain]::new()
try {
    $chain.ChainPolicy.RevocationMode = [Security.Cryptography.X509Certificates.X509RevocationMode]::Online
    $chain.ChainPolicy.UrlRetrievalTimeout = [TimeSpan]::FromSeconds(20)
    $chain.ChainPolicy.ApplicationPolicy.Add([Security.Cryptography.Oid]::new('1.3.6.1.5.5.7.3.3'))
    if (-not $chain.Build($cert[0])) { throw 'Windows could not validate the selected code-signing certificate chain; no trust setting was changed.' }
} finally { $chain.Dispose() }
$timestamp = $null
if (-not [Uri]::TryCreate($TimestampUrl,[UriKind]::Absolute,[ref]$timestamp) -or $timestamp.Scheme -ne 'https') { throw 'Supply the HTTPS RFC3161 timestamp URL recommended by your certificate provider.' }
$configPath = Join-Path $project 'apps\launcher\src-tauri\tauri-signing.local.json'
if (Test-Path -LiteralPath $configPath) { throw 'A local signing configuration already exists; refusing to overwrite it.' }
$config = Get-Content -LiteralPath (Join-Path $project 'apps\launcher\src-tauri\tauri.conf.json') -Raw | ConvertFrom-Json -AsHashtable
$config.bundle.windows.certificateThumbprint = $thumb
$config.bundle.windows.digestAlgorithm = 'sha256'
$config.bundle.windows.timestampUrl = $timestamp.AbsoluteUri
$config.bundle.windows.tsp = $true
try {
    $config | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $configPath -Encoding utf8
    & (Join-Path $PSScriptRoot 'stage-core.ps1')
    if (-not (Get-Command cargo -ErrorAction SilentlyContinue)) { . (Join-Path $PSScriptRoot 'local-toolchain.ps1') }
    Push-Location (Join-Path $project 'apps\launcher')
    try {
        & node (Join-Path $project 'node_modules\@tauri-apps\cli\tauri.js') build --bundles nsis --config $configPath
        if ($LASTEXITCODE -ne 0) { throw 'Signed Tauri build failed.' }
    } finally { Pop-Location }
    $version = $config.version
    $artifacts = @((Join-Path $project 'apps\launcher\src-tauri\target\release\votex-launcher.exe'), (Join-Path $project "apps\launcher\src-tauri\target\release\bundle\nsis\Votex Client_${version}_x64-setup.exe"))
    foreach ($file in $artifacts) {
        $signature = Get-AuthenticodeSignature -LiteralPath $file
        if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Thumbprint -ne $thumb -or $null -eq $signature.TimeStamperCertificate) { throw "Signed artifact verification failed: $file" }
    }
    Write-Output 'Both launcher and installer have valid timestamped signatures. A real Smart App Control execution check is still required before claiming acceptance.'
} finally {
    if (Test-Path -LiteralPath $configPath) { Remove-Item -LiteralPath $configPath }
}
