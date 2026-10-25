$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
  Push-Location frontend
  try {
    npm ci
    if ($LASTEXITCODE) { throw 'npm ci failed' }
    npm run build
    if ($LASTEXITCODE) { throw 'Frontend build failed' }
  } finally { Pop-Location }
  New-Item -ItemType Directory -Force target/classes/static | Out-Null
  ./mvnw.cmd verify
  if ($LASTEXITCODE) { throw 'Maven verification failed' }
  Copy-Item -Path frontend/dist/* -Destination target/classes/static -Recurse -Force
  ./mvnw.cmd -DskipTests package
  if ($LASTEXITCODE) { throw 'Packaging failed' }
} finally { Pop-Location }
