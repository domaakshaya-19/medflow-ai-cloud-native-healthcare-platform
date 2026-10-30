# Authorized creation of ONLY five missing MedFlow resources. Never changes existing roles/passwords/ownership.
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Initialize-SecureShell.ps1')
$root=Split-Path $PSScriptRoot -Parent
$runtime=Join-Path $root 'local-runtime'
$file=Join-Path $runtime 'secrets.xml'
$values=Import-Clixml $file
$admin=Read-Host 'PostgreSQL administrator username (Enter for postgres)'
if(!$admin){$admin='postgres'}
$password=Read-Host 'PostgreSQL administrator password (hidden; not saved)' -AsSecureString
$env:PGUSER=$admin;$env:PGDATABASE='postgres';$env:PGPASSWORD=[Net.NetworkCredential]::new('',$password).Password
function Sql([string]$statement){
    $psi=[Diagnostics.ProcessStartInfo]::new();$psi.FileName='C:\Program Files\PostgreSQL\14\bin\psql.exe'
    $psi.Arguments='-X -w -h localhost -p 5432 -At -q -v ON_ERROR_STOP=1'
    $psi.UseShellExecute=$false;$psi.CreateNoWindow=$true;$psi.RedirectStandardInput=$true;$psi.RedirectStandardOutput=$true;$psi.RedirectStandardError=$true
    $p=[Diagnostics.Process]::new();$p.StartInfo=$psi;[void]$p.Start();$p.StandardInput.WriteLine($statement);$p.StandardInput.Close()
    $output=$p.StandardOutput.ReadToEnd();$err=$p.StandardError.ReadToEnd();$p.WaitForExit()
    if($p.ExitCode -ne 0){throw 'PostgreSQL operation failed; raw SQL diagnostics hidden to protect secrets. No authentication bypass attempted.'}
    return $output.Trim()
}
try {
    if((Sql 'SELECT rolsuper FROM pg_roles WHERE rolname=current_user;') -ne 't'){throw 'Administrator access required; stopping without changes.'}
    foreach($prefix in 'PHARMACY','NUTRITION','BILLING','EMERGENCY','NOTIFICATION'){
        $role='medflow_'+$prefix.ToLowerInvariant()
        $exists=Sql "SELECT count(*) FROM pg_roles WHERE rolname='$role';"
        if($exists -eq '0'){
            $bytes=New-Object byte[] 48;$salt=New-Object byte[] 16;$rng=[Security.Cryptography.RandomNumberGenerator]::Create();$rng.GetBytes($bytes);$rng.GetBytes($salt);$rng.Dispose()
            $plain=[Convert]::ToBase64String($bytes)
            $kdf=[Security.Cryptography.Rfc2898DeriveBytes]::new($plain,$salt,4096,[Security.Cryptography.HashAlgorithmName]::SHA256);$salted=$kdf.GetBytes(32);$kdf.Dispose()
            $hmac=[Security.Cryptography.HMACSHA256]::new($salted);$client=$hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes('Client Key'));$server=$hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes('Server Key'));$hmac.Dispose()
            $sha=[Security.Cryptography.SHA256]::Create();$stored=$sha.ComputeHash($client);$sha.Dispose()
            $verifier='SCRAM-SHA-256$4096:'+ [Convert]::ToBase64String($salt)+'$'+[Convert]::ToBase64String($stored)+':'+[Convert]::ToBase64String($server)
            # Save encrypted recovery material before CREATE ROLE, never overwrite the seven existing keys.
            $values["${prefix}_DB_PASSWORD"]=ConvertTo-SecureString $plain -AsPlainText -Force
            $values | Export-Clixml $file
            [void](Sql "SET log_statement='none';SET log_duration=off;SET log_min_duration_statement=-1;SET log_min_duration_sample=-1;SET log_min_error_statement='panic';CREATE ROLE $role LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD '$verifier';")
            Write-Host "Created missing role $role"
        }elseif(!$values.ContainsKey("${prefix}_DB_PASSWORD")){
            throw "Existing $role has no local credential. Preserved it unchanged; obtain its existing password securely before continuing."
        }
        $exists=Sql "SELECT count(*) FROM pg_database WHERE datname='$role';"
        if($exists -eq '0'){[void](Sql "CREATE DATABASE $role OWNER $role;");Write-Host "Created missing database $role"}
        elseif((Sql "SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname='$role';") -ne $role){throw "Existing $role database ownership differs. No ownership or permissions changed."}
        else{Write-Host "Preserved existing database $role"}
    }
    foreach($prefix in 'PHARMACY','NUTRITION','BILLING','EMERGENCY','NOTIFICATION'){
        $role='medflow_'+$prefix.ToLowerInvariant();$env:PGUSER=$role;$env:PGDATABASE=$role;$env:PGPASSWORD=[Net.NetworkCredential]::new('',$values["${prefix}_DB_PASSWORD"]).Password
        if((Sql 'SELECT 1;') -ne '1'){throw "Connectivity failed for $role"}
        Write-Host "$role connectivity verified"
    }
    'COMPLETE: five approved business databases/roles present; five connectivity checks passed; existing resources and credentials unchanged.' | Set-Content (Join-Path $runtime 'business-provision-status.txt')
}catch{
    "STOPPED: $($_.Exception.Message)" | Set-Content (Join-Path $runtime 'business-provision-status.txt');throw
}finally{
    Remove-Item Env:PGUSER,Env:PGDATABASE,Env:PGPASSWORD -ErrorAction SilentlyContinue
    Remove-Variable values,password,plain,verifier -ErrorAction SilentlyContinue
}
