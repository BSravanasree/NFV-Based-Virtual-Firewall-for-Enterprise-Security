<#
.SYNOPSIS
  Publishes the local NEXUS NFV stack through a free Cloudflare Quick Tunnel (https://*.trycloudflare.com).

.DESCRIPTION
  start : ensures Docker + the compose stack are healthy, ensures a signature-verified cloudflared,
          starts a quick tunnel to the nginx reverse proxy (UI + /api on one hostname), verifies the
          public URL and keeps running until the tunnel stops.
  stop  : stops only the cloudflared process started by "start" (containers and data are untouched).
  status: prints the current public URL and tunnel state.

  Quick Tunnels need no Cloudflare account. The URL changes every time the tunnel restarts and the
  application is only reachable while this computer, Docker and the tunnel are running.
#>
param([ValidateSet('start', 'stop', 'status')][string]$Action = 'start')

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$StateDir    = Join-Path $env:LOCALAPPDATA 'nexus-nfv'
$ToolDir     = Join-Path $env:LOCALAPPDATA 'cloudflared'
$PidFile     = Join-Path $StateDir 'tunnel.pid'
$LogFile     = Join-Path $StateDir 'tunnel.log'
$UrlFile     = Join-Path $StateDir 'public-url.txt'
$OriginPort  = 3000                                   # nginx reverse proxy published by docker-compose.yml
$Origin      = "http://localhost:$OriginPort"
New-Item -ItemType Directory -Force -Path $StateDir | Out-Null

function Say([string]$msg, [string]$color = 'Cyan') { Write-Host "==> $msg" -ForegroundColor $color }
function Fail([string]$msg) { Write-Host "ERROR: $msg" -ForegroundColor Red; exit 1 }

function Get-TunnelProcess {
    if (-not (Test-Path $PidFile)) { return $null }
    $tunnelPid = (Get-Content $PidFile -Raw).Trim()
    if (-not $tunnelPid) { return $null }
    $p = Get-Process -Id $tunnelPid -ErrorAction SilentlyContinue
    if ($p -and $p.ProcessName -eq 'cloudflared') { return $p }
    return $null
}

function Stop-Tunnel {
    $p = Get-TunnelProcess
    if ($p) {
        Stop-Process -Id $p.Id -Force
        Say "Cloudflare tunnel (PID $($p.Id)) stopped." 'Green'
    } else {
        Say 'No tunnel started by NEXUS NFV is running.' 'Yellow'
    }
    Remove-Item $PidFile, $UrlFile -ErrorAction SilentlyContinue
    Write-Host ''
    Write-Host 'Docker containers and the database were NOT touched.'
    Write-Host 'To stop the application as well (data is kept):  docker compose --profile lab stop'
}

function Test-Url([string]$url) {
    try { return (Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 15).StatusCode } catch {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode } return 0
    }
}

function Ensure-Docker {
    docker info --format '{{.ServerVersion}}' *> $null
    if ($LASTEXITCODE -eq 0) { return }
    $desktop = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
    if (-not (Test-Path $desktop)) { Fail 'Docker Desktop is not installed (https://docs.docker.com/desktop/setup/install/windows-install/).' }
    Say 'Starting Docker Desktop...'
    Start-Process $desktop | Out-Null
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 3
        docker info --format '{{.ServerVersion}}' *> $null
        if ($LASTEXITCODE -eq 0) { return }
    }
    Fail 'Docker Desktop did not become ready within 3 minutes.'
}

function Wait-Healthy([string]$container, [int]$timeoutSec = 240) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        $s = (docker inspect -f '{{.State.Health.Status}}' $container 2>$null)
        if ($s -eq 'healthy') { return }
        Start-Sleep -Seconds 3
    }
    Fail "$container did not become healthy (docker compose logs $($container -replace 'nexus-nfv-','' -replace '-1$',''))."
}

function Ensure-Cloudflared {
    $cmd = Get-Command cloudflared -ErrorAction SilentlyContinue
    $exe = if ($cmd) { $cmd.Source } else { Join-Path $ToolDir 'cloudflared.exe' }
    if (-not (Test-Path $exe)) {
        Say 'cloudflared not found - downloading the official release from github.com/cloudflare/cloudflared'
        New-Item -ItemType Directory -Force -Path $ToolDir | Out-Null
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        $arch = if ([Environment]::Is64BitOperatingSystem) { 'amd64' } else { '386' }
        Invoke-WebRequest -UseBasicParsing -OutFile $exe `
            -Uri "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-$arch.exe"
    }
    $sig = Get-AuthenticodeSignature $exe
    if ($sig.Status -ne 'Valid' -or $sig.SignerCertificate.Subject -notmatch 'Cloudflare') {
        Remove-Item $exe -ErrorAction SilentlyContinue
        Fail "cloudflared signature check failed ($($sig.Status)). The file was removed."
    }
    return $exe
}

function Write-Banner([string]$url) {
    $line = '=' * 56
    Write-Host ''
    Write-Host $line -ForegroundColor DarkCyan
    Write-Host '              NEXUS NFV PUBLIC ACCESS' -ForegroundColor White
    Write-Host $line -ForegroundColor DarkCyan
    Write-Host ''
    Write-Host 'Application:      NEXUS NFV'
    Write-Host "Local URL:        $Origin"
    Write-Host ''
    Write-Host 'Public HTTPS URL:'
    Write-Host "  $url" -ForegroundColor Green
    Write-Host "  Console:        $url/overview"
    Write-Host "  API health:     $url/actuator/health"
    Write-Host ''
    Write-Host 'Status:           TUNNEL RUNNING' -ForegroundColor Green
    Write-Host ''
    Write-Host 'Keep this window open while sharing the link.'
    Write-Host 'The URL changes every time the tunnel restarts.'
    Write-Host 'Stop sharing:     run stop-nexus-public.bat (or close this window)'
    Write-Host $line -ForegroundColor DarkCyan
}

# ------------------------------------------------------------------------------------------------
switch ($Action) {
    'stop' { Stop-Tunnel; exit 0 }
    'status' {
        $p = Get-TunnelProcess
        if ($p -and (Test-Path $UrlFile)) { Write-Host "TUNNEL RUNNING (PID $($p.Id)): $(Get-Content $UrlFile -Raw)" -ForegroundColor Green }
        else { Write-Host 'TUNNEL NOT RUNNING' -ForegroundColor Yellow }
        exit 0
    }
}

Set-Location $ProjectRoot
if (-not (Test-Path (Join-Path $ProjectRoot '.env'))) { Fail '.env is missing. Copy .env.example to .env and set every secret first.' }

$existing = Get-TunnelProcess
if ($existing -and (Test-Path $UrlFile)) {
    $url = (Get-Content $UrlFile -Raw).Trim()
    Say "A tunnel is already running (PID $($existing.Id))." 'Yellow'
    Write-Banner $url
    Wait-Process -Id $existing.Id
    exit 0
}

Say 'Checking Docker Desktop'
Ensure-Docker

Say 'Starting the NEXUS NFV stack (existing containers and data are reused)'
docker compose --profile lab up -d | Out-Host
if ($LASTEXITCODE -ne 0) { Fail 'docker compose up failed.' }
docker image inspect nexus/fw-vnf:1.0.0 *> $null
if ($LASTEXITCODE -ne 0) {
    Say 'Firewall VNF image not built yet - building it once (about 10 minutes)...' 'Yellow'
    docker compose --profile build build fw-vnf-image | Out-Host
}

Say 'Waiting for MySQL, backend and the reverse proxy to become healthy'
Wait-Healthy 'nexus-nfv-mysql-1'
Wait-Healthy 'nexus-nfv-backend-1'
Wait-Healthy 'nexus-nfv-frontend-1'
$code = Test-Url "$Origin/actuator/health"
if ($code -ne 200) { Fail "Origin $Origin/actuator/health answered HTTP $code - not starting the tunnel." }
Say "Origin verified: $Origin (nginx -> UI, /api, Spring Boot)" 'Green'

$exe = Ensure-Cloudflared
Say "Using $exe ($((& $exe --version) -join ' '))"

Remove-Item $LogFile, $UrlFile -ErrorAction SilentlyContinue
$proc = Start-Process -FilePath $exe -PassThru -WindowStyle Hidden `
    -ArgumentList @('tunnel', '--no-autoupdate', '--url', $Origin, '--logfile', $LogFile, '--loglevel', 'info')
Set-Content -Path $PidFile -Value $proc.Id

Say 'Waiting for Cloudflare to assign the public hostname'
$url = $null
for ($i = 0; $i -lt 60 -and -not $url; $i++) {
    Start-Sleep -Seconds 1
    if ($proc.HasExited) { Get-Content $LogFile -Tail 20 -ErrorAction SilentlyContinue; Remove-Item $PidFile; Fail 'cloudflared exited.' }
    if (Test-Path $LogFile) {
        $m = Select-String -Path $LogFile -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' -AllMatches | Select-Object -First 1
        if ($m) { $url = $m.Matches[0].Value }
    }
}
if (-not $url) { Stop-Process -Id $proc.Id -Force; Remove-Item $PidFile; Fail "No trycloudflare.com URL in $LogFile within 60 s." }

Say "Verifying $url (DNS propagation can take a few seconds)"
$ok = $false
for ($i = 0; $i -lt 30 -and -not $ok; $i++) {
    if ((Test-Url "$url/actuator/health") -eq 200 -and (Test-Url "$url/") -eq 200) { $ok = $true } else { Start-Sleep -Seconds 3 }
}
if (-not $ok) { Write-Host "WARNING: $url is not answering yet; it may need another minute." -ForegroundColor Yellow }
Set-Content -Path $UrlFile -Value $url

Write-Banner $url
Wait-Process -Id $proc.Id
Remove-Item $PidFile, $UrlFile -ErrorAction SilentlyContinue
Say 'Tunnel stopped.' 'Yellow'
