# User approved creation of missing development databases and roles on 2026-10-30.
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Initialize-SecureShell.ps1')
$root=Split-Path $PSScriptRoot -Parent
Set-Location $root
$dir=Join-Path $root 'local-runtime'
New-Item -ItemType Directory -Force $dir | Out-Null
$file=Join-Path $dir 'secrets.xml'
$values=@{}
if (Test-Path $file) { $values=Import-Clixml $file }
$admin=Read-Host 'PostgreSQL administrator username (Enter for postgres)'
if (!$admin) { $admin='postgres' }
$password=Read-Host 'PostgreSQL administrator password (hidden)' -AsSecureString
$env:PGPASSWORD=[System.Net.NetworkCredential]::new('',$password).Password
$psql='C:\Program Files\PostgreSQL\14\bin\psql.exe'
function Sql([string]$statement) {
    $psi=New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName=$psql
    # Administrator username is supplied in the environment rather than interpolated into arguments.
    $psi.Arguments='-X -w -h localhost -p 5432 -d postgres -At -v ON_ERROR_STOP=1'
    $psi.UseShellExecute=$false; $psi.CreateNoWindow=$true
    $psi.RedirectStandardInput=$true; $psi.RedirectStandardOutput=$true; $psi.RedirectStandardError=$true
    $p=New-Object System.Diagnostics.Process; $p.StartInfo=$psi
    [void]$p.Start(); $p.StandardInput.WriteLine($statement); $p.StandardInput.Close()
    $output=$p.StandardOutput.ReadToEnd(); $err=$p.StandardError.ReadToEnd(); $p.WaitForExit()
    if ($p.ExitCode -ne 0) { throw 'PostgreSQL operation failed. Check administrator credentials/privileges locally. Raw SQL errors are hidden to protect secrets.' }
    return $output.Trim()
}
$env:PGUSER=$admin
try {
    [void](Sql 'SELECT 1;')
    foreach ($prefix in 'AUTH','PATIENT','DOCTOR','APPOINTMENT','MEDICAL_RECORDS','LAB','PRESCRIPTION') {
        $db='medflow_'+$prefix.ToLowerInvariant()
        $exists=Sql "SELECT count(*) FROM pg_roles WHERE rolname='$db';"
        if ($exists -eq '0') {
            $bytes=New-Object byte[] 36; $rng=[Security.Cryptography.RandomNumberGenerator]::Create(); $rng.GetBytes($bytes); $rng.Dispose()
            $secret=[Convert]::ToBase64String($bytes)
            [void](Sql "CREATE ROLE $db LOGIN PASSWORD '$secret';")
            $values["${prefix}_DB_PASSWORD"]=ConvertTo-SecureString $secret -AsPlainText -Force
            $values | Export-Clixml $file
            Write-Host "Created missing role $db"
        } elseif (!$values.ContainsKey("${prefix}_DB_PASSWORD")) {
            $values["${prefix}_DB_PASSWORD"]=Read-Host "Existing $db role password (unchanged)" -AsSecureString
            $values | Export-Clixml $file
        }
        $exists=Sql "SELECT count(*) FROM pg_database WHERE datname='$db';"
        if ($exists -eq '0') { [void](Sql "CREATE DATABASE $db OWNER $db;"); Write-Host "Created missing database $db" }
        else { Write-Host "Preserved existing database $db" }
    }
    if (!$values.ContainsKey('JWT_SECRET')) {
        $bytes=New-Object byte[] 48; $rng=[Security.Cryptography.RandomNumberGenerator]::Create(); $rng.GetBytes($bytes); $rng.Dispose()
        $values.JWT_SECRET=ConvertTo-SecureString ([Convert]::ToBase64String($bytes)) -AsPlainText -Force
    }
    if (!$values.ContainsKey('ADMIN_BOOTSTRAP_PASSWORD')) {
        $values.ADMIN_BOOTSTRAP_PASSWORD=Read-Host 'Choose strong synthetic demo admin password (10+ characters, upper/lower/number/symbol)' -AsSecureString
    }
    $values | Export-Clixml $file
    'Database provisioning completed; existing roles and databases preserved.' | Set-Content (Join-Path $dir 'provision-status.txt')
} finally {
    Remove-Item Env:PGPASSWORD,Env:PGUSER -ErrorAction SilentlyContinue
    Remove-Variable password,secret,values -ErrorAction SilentlyContinue
}
& (Join-Path $PSScriptRoot 'Start-Local.ps1')
Write-Host 'Waiting 35 seconds for discovery caches before synthetic API verification...'
Start-Sleep -Seconds 35
& (Join-Path $PSScriptRoot 'Test-Demo.ps1')
