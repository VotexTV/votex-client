[CmdletBinding()]
param(
 [Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9.-]{1,253}$')][string]$PublicHost,
 [Parameter(Mandatory=$true)][string]$Destination,
 [ValidateRange(1024,65535)][int]$LocalMinecraftPort=25565,
 [switch]$DownloadBinaries
)
$ErrorActionPreference='Stop'
$target=[IO.Path]::GetFullPath($Destination)
if($target -eq [IO.Path]::GetPathRoot($target)){throw 'Bitte einen eigenen Tunnel-Unterordner verwenden.'}
if((Test-Path -LiteralPath $target) -and (Get-ChildItem -LiteralPath $target -Force | Select-Object -First 1)){throw 'Der Zielordner muss leer sein. Bestehende Schlüssel werden nicht ersetzt.'}
$server=New-Item -ItemType Directory -Path (Join-Path $target 'public-server') -Force
$client=New-Item -ItemType Directory -Path (Join-Path $target 'world-host') -Force
function Write-Utf8([string]$Path,[string]$Text){[IO.File]::WriteAllText($Path,$Text,[Text.UTF8Encoding]::new($false))}
$now=[DateTimeOffset]::UtcNow
$caKey=[Security.Cryptography.RSA]::Create(3072)
$caRequest=[Security.Cryptography.X509Certificates.CertificateRequest]::new('CN=Votex private tunnel CA',$caKey,[Security.Cryptography.HashAlgorithmName]::SHA256,[Security.Cryptography.RSASignaturePadding]::Pkcs1)
$caRequest.CertificateExtensions.Add([Security.Cryptography.X509Certificates.X509BasicConstraintsExtension]::new($true,$false,0,$true))
$caRequest.CertificateExtensions.Add([Security.Cryptography.X509Certificates.X509KeyUsageExtension]::new([Security.Cryptography.X509Certificates.X509KeyUsageFlags]::KeyCertSign -bor [Security.Cryptography.X509Certificates.X509KeyUsageFlags]::CrlSign,$true))
$ca=$caRequest.CreateSelfSigned($now.AddMinutes(-5),$now.AddYears(2))
function New-TunnelCertificate([string]$CommonName,[string]$Eku,[string]$Folder,[string]$Stem,[bool]$IsServer){
 $key=[Security.Cryptography.RSA]::Create(3072)
 try {
  $request=[Security.Cryptography.X509Certificates.CertificateRequest]::new(('CN='+$CommonName),$key,[Security.Cryptography.HashAlgorithmName]::SHA256,[Security.Cryptography.RSASignaturePadding]::Pkcs1)
  $request.CertificateExtensions.Add([Security.Cryptography.X509Certificates.X509BasicConstraintsExtension]::new($false,$false,0,$true))
  $request.CertificateExtensions.Add([Security.Cryptography.X509Certificates.X509KeyUsageExtension]::new([Security.Cryptography.X509Certificates.X509KeyUsageFlags]::DigitalSignature -bor [Security.Cryptography.X509Certificates.X509KeyUsageFlags]::KeyEncipherment,$true))
  $oids=[Security.Cryptography.OidCollection]::new();$oids.Add([Security.Cryptography.Oid]::new($Eku)) | Out-Null
  $request.CertificateExtensions.Add([Security.Cryptography.X509Certificates.X509EnhancedKeyUsageExtension]::new($oids,$true))
  if($IsServer){$names=[Security.Cryptography.X509Certificates.SubjectAlternativeNameBuilder]::new();$ip=[Net.IPAddress]::None;if([Net.IPAddress]::TryParse($PublicHost,[ref]$ip)){$names.AddIpAddress($ip)}else{$names.AddDnsName($PublicHost)};$request.CertificateExtensions.Add($names.Build())}
  $serial=[Security.Cryptography.RandomNumberGenerator]::GetBytes(16)
  $certificate=$request.Create($ca,$now.AddMinutes(-5),$now.AddYears(1),$serial)
  try {Write-Utf8 (Join-Path $Folder ($Stem+'.crt')) $certificate.ExportCertificatePem();Write-Utf8 (Join-Path $Folder ($Stem+'.key')) $key.ExportPkcs8PrivateKeyPem()} finally {$certificate.Dispose()}
 } finally {$key.Dispose()}
}
try {
 New-TunnelCertificate $PublicHost '1.3.6.1.5.5.7.3.1' $server.FullName 'server' $true
 New-TunnelCertificate 'votex-world-host' '1.3.6.1.5.5.7.3.2' $client.FullName 'client' $false
 Write-Utf8 (Join-Path $server.FullName 'ca.crt') $ca.ExportCertificatePem()
 Write-Utf8 (Join-Path $client.FullName 'ca.crt') $ca.ExportCertificatePem()
} finally {$ca.Dispose();$caKey.Dispose()}
$token=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant()
Write-Utf8 (Join-Path $server.FullName 'frps.toml') @"
bindAddr = "0.0.0.0"
bindPort = 7000
proxyBindAddr = "0.0.0.0"
auth.method = "token"
auth.token = "$token"
auth.additionalScopes = ["HeartBeats", "NewWorkConns"]
allowPorts = [{single = 25565}, {single = 24454}]
maxPortsPerClient = 2
transport.tls.force = true
transport.tls.certFile = "server.crt"
transport.tls.keyFile = "server.key"
transport.tls.trustedCaFile = "ca.crt"
log.to = "console"
log.level = "info"
"@
Write-Utf8 (Join-Path $client.FullName 'frpc.toml') @"
serverAddr = "$PublicHost"
serverPort = 7000
user = "votex"
auth.method = "token"
auth.token = "$token"
auth.additionalScopes = ["HeartBeats", "NewWorkConns"]
transport.tls.enable = true
transport.tls.serverName = "$PublicHost"
transport.tls.certFile = "client.crt"
transport.tls.keyFile = "client.key"
transport.tls.trustedCaFile = "ca.crt"
log.to = "console"
log.level = "info"

[[proxies]]
name = "minecraft"
type = "tcp"
localIP = "127.0.0.1"
localPort = $LocalMinecraftPort
remotePort = 25565

[[proxies]]
name = "voice"
type = "udp"
localIP = "127.0.0.1"
localPort = 24454
remotePort = 24454
"@
if($DownloadBinaries){
 $archives=New-Item -ItemType Directory -Path (Join-Path $target 'verified-downloads') -Force
 foreach($download in @(
  @{Name='frp_0.71.1_windows_amd64.zip';Sha='b90074b2b6a1d792a7a40f085ac6d276bcab2d1bc60201f8a86ca53ce65ce821'},
  @{Name='frp_0.71.1_linux_amd64.tar.gz';Sha='1d9875210972a5d1a62b2dc4c48df45ab0af05d3ca1b5e494a014428259df803'})){
  $file=Join-Path $archives.FullName $download.Name
  Invoke-WebRequest -Uri ('https://github.com/fatedier/frp/releases/download/v0.71.1/'+$download.Name) -OutFile $file -MaximumRedirection 5
  if((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() -ne $download.Sha){Remove-Item -LiteralPath $file;throw 'FRP-Download-Prüfsumme stimmt nicht.'}
 }
}
Write-Output ('Private Tunnelkonfiguration vorbereitet: '+$target)
Write-Output 'Schlüssel und auth.token sind nur für die beiden Betreibergeräte. Nichts davon auf der Website veröffentlichen oder an Spieler verteilen.'
Write-Output 'Es läuft noch kein Tunnel. Der öffentliche Server benötigt eine erreichbare Adresse und die Ports 7000/TCP, 25565/TCP und 24454/UDP.'
