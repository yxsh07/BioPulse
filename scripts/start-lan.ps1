param(
  [switch]$FirewallOnly,
  [int]$InterfaceIndex = 0
)

$ErrorActionPreference = 'Stop'
$ruleName = 'BioPulse local Wi-Fi host (TCP 8080)'
$projectRoot = Split-Path $PSScriptRoot -Parent

function New-SecureHex([int]$byteCount = 32) {
  $bytes = [byte[]]::new($byteCount)
  $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
  try { $random.GetBytes($bytes) } finally { $random.Dispose() }
  return -join ($bytes | ForEach-Object { $_.ToString('x2') })
}

function Set-DotEnvValue([string]$path, [string]$key, [string]$value) {
  $content = if (Test-Path -LiteralPath $path) {
    Get-Content -LiteralPath $path -Raw
  } else {
    ''
  }
  $pattern = '(?m)^' + [regex]::Escape($key) + '=[^\r\n]*'
  if ([regex]::IsMatch($content, $pattern)) {
    $content = [regex]::Replace($content, $pattern, "$key=$value")
  } else {
    if ($content.Length -gt 0 -and -not $content.EndsWith("`n")) { $content += "`r`n" }
    $content += "$key=$value`r`n"
  }
  [System.IO.File]::WriteAllText(
    $path,
    $content.TrimEnd("`r", "`n") + "`r`n",
    [System.Text.UTF8Encoding]::new($false)
  )
}

function Test-DockerEngine {
  $previousPreference = $ErrorActionPreference
  try {
    $ErrorActionPreference = 'Continue'
    docker info --format '{{.ServerVersion}}' *> $null
    return $LASTEXITCODE -eq 0
  } finally {
    $ErrorActionPreference = $previousPreference
  }
}

function Test-DockerVolume([string]$name) {
  $previousPreference = $ErrorActionPreference
  try {
    $ErrorActionPreference = 'Continue'
    docker volume inspect $name *> $null
    return $LASTEXITCODE -eq 0
  } finally {
    $ErrorActionPreference = $previousPreference
  }
}

function Get-WifiConfiguration {
  $candidates = @(Get-NetIPConfiguration | Where-Object {
    $_.NetAdapter.Status -eq 'Up' -and
    $_.IPv4DefaultGateway -and
    $_.IPv4Address -and
    $_.InterfaceAlias -match '(?i)wi-?fi|wlan'
  })
  if ($InterfaceIndex -gt 0) {
    $candidates = @($candidates | Where-Object InterfaceIndex -eq $InterfaceIndex)
  }
  if ($candidates.Count -eq 0) {
    throw 'No connected Wi-Fi adapter with an IPv4 gateway was found. Connect this PC to Wi-Fi and run the command again.'
  }
  if ($candidates.Count -gt 1) {
    Write-Host 'Connected Wi-Fi adapters:'
    for ($i = 0; $i -lt $candidates.Count; $i++) {
      $address = $candidates[$i].IPv4Address | Select-Object -First 1
      Write-Host "[$i] $($candidates[$i].InterfaceAlias)  $($address.IPAddress)"
    }
    $choice = Read-Host 'Choose the Wi-Fi adapter connected to the devices'
    $selected = 0
    if (-not [int]::TryParse($choice, [ref]$selected) -or $selected -lt 0 -or $selected -ge $candidates.Count) {
      throw 'Invalid Wi-Fi adapter selection.'
    }
    return $candidates[$selected]
  }
  return $candidates[0]
}

function Ensure-PrivateFirewallRule([int]$Index) {
  $profile = Get-NetConnectionProfile -InterfaceIndex $Index -ErrorAction SilentlyContinue |
    Where-Object NetworkCategory -eq 'Private' | Select-Object -First 1
  if (-not $profile) {
    throw 'The connected Wi-Fi is not marked Private in Windows. In Settings > Network & internet > Wi-Fi > your connected network, set Network profile type to Private, then run this command again. BioPulse only opens its firewall rule on Private networks.'
  }

  $rules = @(Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue)
  if ($rules.Count -gt 0) {
    $correct = $false
    foreach ($rule in $rules) {
      $port = $rule | Get-NetFirewallPortFilter
      $address = $rule | Get-NetFirewallAddressFilter
      if ($rule.Enabled -eq 'True' -and $rule.Direction -eq 'Inbound' -and
          $rule.Action -eq 'Allow' -and $rule.Profile.ToString().Contains('Private') -and
          $port.Protocol -eq 'TCP' -and $port.LocalPort -contains '8080' -and
          $address.RemoteAddress -contains 'LocalSubnet') {
        $correct = $true
      }
    }
    if ($correct) { return }
    throw "A Windows Firewall rule named '$ruleName' already exists with different settings. Review it in Windows Defender Firewall with Advanced Security before running this command again."
  }

  $principal = [Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
  if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    $scriptPath = $PSCommandPath
    $arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$scriptPath`" -FirewallOnly -InterfaceIndex $Index"
    $powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
    $elevated = Start-Process -FilePath $powershell -ArgumentList $arguments -Verb RunAs -WindowStyle Hidden -Wait -PassThru
    if ($elevated.ExitCode -ne 0) {
      throw 'Could not add the local-only Windows Firewall rule. Approve the Windows elevation prompt and retry.'
    }
    return
  }

  New-NetFirewallRule -DisplayName $ruleName -Direction Inbound -Action Allow `
    -Protocol TCP -LocalPort 8080 -RemoteAddress LocalSubnet -Profile Private | Out-Null
}

try {
  $wifi = Get-WifiConfiguration
  $ip = ($wifi.IPv4Address | Where-Object { $_.IPAddress -notlike '169.254.*' } | Select-Object -First 1).IPAddress
  if (-not $ip) { throw "Wi-Fi adapter '$($wifi.InterfaceAlias)' has no usable IPv4 address." }

  if ($FirewallOnly) {
    Ensure-PrivateFirewallRule -Index $wifi.InterfaceIndex
    exit 0
  }

  if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker Desktop is not installed. Install and start Docker Desktop, then run this command again.'
  }
  if (-not (Test-DockerEngine)) { throw 'Docker Desktop is open, but its Linux container engine is not ready. Wait for Docker Desktop to say it is running, then retry.' }
  Ensure-PrivateFirewallRule -Index $wifi.InterfaceIndex
  Push-Location $projectRoot
  try {
    $envPath = Join-Path $projectRoot '.env'
    $createdEnv = -not (Test-Path -LiteralPath $envPath)
    $hadDatabaseVolume = Test-DockerVolume 'biopulse_mysql_data'
    if ($hadDatabaseVolume -and $createdEnv) {
      throw 'The BioPulse database volume already exists, but .env is missing. Restore the original ignored .env file before starting so the app can reconnect to MySQL without changing access to your saved data.'
    }
    if ($createdEnv) {
      Copy-Item -LiteralPath (Join-Path $projectRoot '.env.example') -Destination $envPath
    }
    $envText = if (Test-Path -LiteralPath $envPath) { Get-Content -LiteralPath $envPath -Raw } else { '' }
    foreach ($key in @('DB_PASSWORD', 'MYSQL_ROOT_PASSWORD')) {
      if ($envText -notmatch "(?m)^$key=(?=\S)[^\r\n]*") {
        if ($hadDatabaseVolume) {
          throw "The BioPulse database volume already exists and .env has no $key. Restore the original ignored .env file; generating a new password cannot unlock existing MySQL data."
        }
        $value = New-SecureHex
        Set-DotEnvValue $envPath $key $value
        $envText = Get-Content -LiteralPath $envPath -Raw
      }
    }
    $generatedDemoPassword = $null
    $seedingEnabled = $envText -match '(?m)^SEED_DEMO=true\r?$'
    if ($seedingEnabled -and $envText -notmatch '(?m)^DEMO_PASSWORD=(?=\S)[^\r\n]*') {
      if ($hadDatabaseVolume) {
        throw 'Demo seeding is enabled for an existing database, but DEMO_PASSWORD is missing from .env. Restore the original ignored .env file before continuing.'
      }
      $generatedDemoPassword = New-SecureHex
      Set-DotEnvValue $envPath 'DEMO_PASSWORD' $generatedDemoPassword
      Set-DotEnvValue $envPath 'BIOPULSE_DEMO_PASSWORD_PENDING' 'true'
      $envText = Get-Content -LiteralPath $envPath -Raw
    }
    if ($createdEnv) {
      Set-DotEnvValue $envPath 'SEED_DEMO' 'true'
      $envText = Get-Content -LiteralPath $envPath -Raw
    }

    # Same-origin requests need no CORS. WebSocket handshakes still send Origin,
    # so allow the current LAN URL plus this machine's loopback origins only.
    $env:BIOPULSE_BIND_ADDRESS = '0.0.0.0'
    $env:ALLOWED_ORIGINS = "http://${ip}:8080,http://localhost:8080,http://127.0.0.1:8080"
    docker compose -p biopulse up -d --build
    if ($LASTEXITCODE -ne 0) { throw 'BioPulse did not start successfully. Review the Docker Compose output above.' }

    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
      try {
        $response = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/api/v1/health' -TimeoutSec 3 -UseBasicParsing
        if ($response.StatusCode -eq 200) { $ready = $true; break }
      } catch { Start-Sleep -Seconds 2 }
    }
    if (-not $ready) {
      docker compose -p biopulse ps
      docker compose -p biopulse logs --tail 80 app
      throw 'BioPulse containers started, but the health endpoint did not become ready within 120 seconds.'
    }

    Write-Host ''
    Write-Host 'BioPulse is ready on this Wi-Fi:' -ForegroundColor Green
    Write-Host "  http://${ip}:8080" -ForegroundColor Cyan
    Write-Host 'Share this address only with devices on your trusted private Wi-Fi.'
    Write-Host 'Keep this PC and Docker Desktop running. The MySQL database persists across restarts.'
    Write-Host 'Stop the server with: docker compose -p biopulse stop'
    if ($envText -match '(?m)^BIOPULSE_DEMO_PASSWORD_PENDING=true\r?$') {
      $demoPasswordLine = [regex]::Match($envText, '(?m)^DEMO_PASSWORD=([^\r\n]+)')
      if (-not $demoPasswordLine.Success) { throw 'The generated demo password is missing from .env.' }
      Write-Host ''
      Write-Host 'First-run synthetic demo account:'
      Write-Host '  admin@biopulse.local'
      Write-Host "  Password: $($demoPasswordLine.Groups[1].Value)"
      Write-Host 'This generated password is also saved in the ignored .env file.'
      Set-DotEnvValue $envPath 'BIOPULSE_DEMO_PASSWORD_PENDING' 'false'
    }
  } finally {
    Pop-Location
  }
} catch {
  Write-Host "BioPulse could not start: $($_.Exception.Message)" -ForegroundColor Red
  exit 1
}
