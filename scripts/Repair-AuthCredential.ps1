# Correct only the locally stored Auth credential. Never ALTER ROLE or modify a database.
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Initialize-SecureShell.ps1')
$root=Split-Path $PSScriptRoot -Parent
Set-Location $root
$file=Join-Path $root 'local-runtime\secrets.xml'
$values=Import-Clixml $file
$candidate=Read-Host 'Existing medflow_auth PostgreSQL ROLE password (not postgres administrator or application login password)' -AsSecureString
$env:PGPASSWORD=[System.Net.NetworkCredential]::new('',$candidate).Password
try {
    $null=& 'C:\Program Files\PostgreSQL\14\bin\psql.exe' -X -w -h localhost -p 5432 -U medflow_auth -d medflow_auth -Atc 'SELECT 1;' 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'Existing Auth database role password did not validate. No stored value or database was changed.' }
    $values.AUTH_DB_PASSWORD=$candidate
    $values | Export-Clixml $file
    Write-Host 'Auth credential validated and local encrypted storage updated. Database and role unchanged.'
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    Remove-Variable candidate,values -ErrorAction SilentlyContinue
}
& (Join-Path $PSScriptRoot 'Start-Local.ps1')
Write-Host 'Waiting 35 seconds for discovery caches before synthetic API verification...'
Start-Sleep -Seconds 35
& (Join-Path $PSScriptRoot 'Test-Demo.ps1')
