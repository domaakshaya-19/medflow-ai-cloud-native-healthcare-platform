$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Initialize-SecureShell.ps1')
$root = Split-Path $PSScriptRoot -Parent
$dir = Join-Path $root 'local-runtime'
New-Item -ItemType Directory -Force $dir | Out-Null
$file = Join-Path $dir 'secrets.xml'
if (Test-Path $file) { throw 'Secure configuration already exists. Preserve it; review privately before replacing.' }
# Export-Clixml encrypts SecureString values using Windows DPAPI for this Windows account.
# No secrets are placed in .env, arguments, console output, or shell history.
$values = @{}
foreach ($name in 'AUTH','PATIENT','DOCTOR','APPOINTMENT','MEDICAL_RECORDS','LAB','PRESCRIPTION') {
    $values["${name}_DB_PASSWORD"] = Read-Host "$name existing development database password" -AsSecureString
}
$values.JWT_SECRET = Read-Host 'Shared JWT secret (at least 32 UTF-8 bytes)' -AsSecureString
$values.ADMIN_BOOTSTRAP_PASSWORD = Read-Host 'Local demo administrator password (strong password)' -AsSecureString
$values | Export-Clixml -LiteralPath $file
Write-Host 'Encrypted local configuration saved for this Windows account. No databases were changed.'
