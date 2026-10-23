$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Install and start Docker Desktop, then run this script again.'
}
if (-not (Test-Path -LiteralPath '.env')) {
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $bytes = New-Object byte[] 24
    $random.GetBytes($bytes)
    $adminPassword = ([BitConverter]::ToString($bytes)).Replace('-', '')
    $random.GetBytes($bytes)
    $appPassword = ([BitConverter]::ToString($bytes)).Replace('-', '')
    $random.Dispose()
    @("POSTGRES_PASSWORD=$adminPassword", "DB_PASSWORD=$appPassword", 'DB_USER=auction_app', 'DB_URL=jdbc:postgresql://localhost:5432/auction') | Set-Content -LiteralPath '.env' -Encoding ascii
}
docker compose up -d --build
if ($LASTEXITCODE -ne 0) { throw 'Startup failed. Check Docker Desktop and docker compose logs.' }
Write-Host 'Open http://localhost:8080/api/users. The first startup may take a few minutes.'
