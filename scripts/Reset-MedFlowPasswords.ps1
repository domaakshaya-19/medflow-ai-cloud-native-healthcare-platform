# Audit first. Password changes require an approval file tied to this exact audit run.
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Initialize-SecureShell.ps1')
$root=Split-Path $PSScriptRoot -Parent
Set-Location $root
$runtime=Join-Path $root 'local-runtime'
$id=[guid]::NewGuid().ToString('N')
$status=Join-Path $runtime 'password-reset-status.txt'
$approval=Join-Path $runtime "approve-reset-$id.json"
$targets=@('medflow_auth','medflow_patient','medflow_doctor','medflow_appointment','medflow_medical_records','medflow_lab','medflow_prescription')
$list=($targets | ForEach-Object { "'$_'" }) -join ','
$admin=Read-Host 'PostgreSQL administrator username (Enter for postgres)'
if (!$admin) { $admin='postgres' }
$credential=Read-Host 'PostgreSQL administrator password (hidden; never saved)' -AsSecureString
$env:PGUSER=$admin
$env:PGPASSWORD=[Net.NetworkCredential]::new('',$credential).Password
$env:PGDATABASE='postgres'
$psql='C:\Program Files\PostgreSQL\14\bin\psql.exe'
function Sql([string]$statement) {
    $psi=[Diagnostics.ProcessStartInfo]::new()
    $psi.FileName=$psql
    $psi.Arguments='-X -w -h localhost -p 5432 -At -q -v ON_ERROR_STOP=1'
    $psi.UseShellExecute=$false; $psi.CreateNoWindow=$true
    $psi.RedirectStandardInput=$true; $psi.RedirectStandardOutput=$true; $psi.RedirectStandardError=$true
    $p=[Diagnostics.Process]::new(); $p.StartInfo=$psi
    [void]$p.Start(); $p.StandardInput.WriteLine($statement); $p.StandardInput.Close()
    $output=$p.StandardOutput.ReadToEnd(); $errorText=$p.StandardError.ReadToEnd(); $p.WaitForExit()
    if ($p.ExitCode -ne 0) { throw 'PostgreSQL operation failed. No authentication bypass was attempted. Raw SQL diagnostics suppressed to protect credentials.' }
    return $output.Trim()
}
function JsonSql([string]$query) {
    $raw=Sql "SELECT COALESCE(json_agg(row_to_json(a)),'[]'::json) FROM ($query) a;"
    # Windows PowerShell can wrap a JSON array as one PSObject in a pipeline.
    $parsed=ConvertFrom-Json -InputObject $raw
    foreach ($row in $parsed) { Write-Output $row }
}
function Audit {
    $roles=JsonSql "SELECT rolname,rolsuper,rolcreatedb,rolcreaterole,rolreplication,rolbypassrls,rolcanlogin FROM pg_roles WHERE rolname IN ($list) ORDER BY rolname"
    $databases=JsonSql 'SELECT datname,pg_get_userbyid(datdba) AS owner FROM pg_database ORDER BY datname'
    $memberships=JsonSql "SELECT pg_get_userbyid(roleid) AS granted_role,pg_get_userbyid(member) AS member,admin_option FROM pg_auth_members WHERE roleid IN (SELECT oid FROM pg_roles WHERE rolname IN ($list)) OR member IN (SELECT oid FROM pg_roles WHERE rolname IN ($list)) ORDER BY 1,2"
    $dependencies=JsonSql "SELECT r.rolname,COALESCE(d.datname,'<shared>') AS database,s.classid::regclass::text AS catalog,s.objid,s.deptype FROM pg_shdepend s JOIN pg_roles r ON s.refobjid=r.oid AND s.refclassid='pg_authid'::regclass LEFT JOIN pg_database d ON d.oid=s.dbid WHERE r.rolname IN ($list) ORDER BY 1,2,3,4,5"
    $shared=JsonSql "SELECT r.rolname,pg_describe_object(s.classid,s.objid,s.objsubid) AS object,s.deptype FROM pg_shdepend s JOIN pg_roles r ON s.refobjid=r.oid AND s.refclassid='pg_authid'::regclass WHERE s.dbid=0 AND r.rolname IN ($list) ORDER BY 1,2"
    return @{roles=$roles;databases=$databases;memberships=$memberships;dependencies=$dependencies;shared=$shared}
}
function ScramVerifier([string]$password) {
    $salt=New-Object byte[] 16; $rng=[Security.Cryptography.RandomNumberGenerator]::Create();$rng.GetBytes($salt);$rng.Dispose()
    $kdf=[Security.Cryptography.Rfc2898DeriveBytes]::new($password,$salt,4096,[Security.Cryptography.HashAlgorithmName]::SHA256)
    $salted=$kdf.GetBytes(32);$kdf.Dispose()
    $hmac=[Security.Cryptography.HMACSHA256]::new($salted)
    $client=$hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes('Client Key'))
    $server=$hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes('Server Key'));$hmac.Dispose()
    $sha=[Security.Cryptography.SHA256]::Create();$stored=$sha.ComputeHash($client);$sha.Dispose()
    return 'SCRAM-SHA-256$4096:'+ [Convert]::ToBase64String($salt)+'$'+[Convert]::ToBase64String($stored)+':'+[Convert]::ToBase64String($server)
}
try {
    $super=Sql 'SELECT rolsuper FROM pg_roles WHERE rolname=current_user;'
    if ($super -ne 't') { throw 'This workflow requires a PostgreSQL administrator to inspect all dependencies. No changes made.' }
    $snapshot=Audit
    if ($snapshot.roles.Count -ne 7) { throw 'Not all seven requested roles exist. No password changes made.' }
    $sessions=JsonSql "SELECT usename,datname,application_name,state FROM pg_stat_activity WHERE usename IN ($list) ORDER BY 1,2,3"
    $logging=JsonSql "SELECT name,setting FROM pg_settings WHERE name IN ('log_statement','log_duration','log_min_duration_statement','log_min_duration_sample','pgaudit.log','shared_preload_libraries') ORDER BY name"
    $details=@()
    foreach ($db in $snapshot.databases) {
        if ($db.datname -in @('template0','template1')) { continue }
        $env:PGDATABASE=$db.datname
        $details+=@(JsonSql "SELECT r.rolname,current_database() AS database,pg_describe_object(s.classid,s.objid,s.objsubid) AS object,s.deptype FROM pg_shdepend s JOIN pg_roles r ON s.refobjid=r.oid AND s.refclassid='pg_authid'::regclass WHERE s.dbid=(SELECT oid FROM pg_database WHERE datname=current_database()) AND r.rolname IN ($list) ORDER BY 1,3")
    }
    $env:PGDATABASE='postgres'
    $report=@{id=$id;time=[DateTime]::UtcNow.ToString('o');snapshot=$snapshot;sessions=$sessions;logging=$logging;objects=$details;approvalFile=$approval}
    $report | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $runtime 'role-scope-audit.json')
    "AUDIT_READY $id : no passwords changed; waiting for scope review." | Set-Content $status
    Write-Host 'Read-only scope audit complete. Waiting for review; administrator credential remains only in this process memory.'
    $deadline=(Get-Date).AddMinutes(30)
    while (!(Test-Path $approval)) {
        if ((Get-Date) -gt $deadline) { throw 'Scope review timed out. No passwords changed.' }
        Start-Sleep -Seconds 2
    }
    $decision=Get-Content $approval -Raw | ConvertFrom-Json
    if ($decision.id -ne $id -or $decision.action -ne 'reset-exact-seven') { throw 'Approval does not match this audit. No changes made.' }
    $current=Audit
    if (($snapshot | ConvertTo-Json -Depth 12 -Compress) -ne ($current | ConvertTo-Json -Depth 12 -Compress)) { throw 'Role dependencies changed since audit. Review again before resetting.' }
    if (@($logging | Where-Object { $_.name -eq 'pgaudit.log' -and $_.setting -notin @('none','') }).Count) { throw 'Database auditing may log password verifiers. Review logging before reset; no changes made.' }
    $secretFile=Join-Path $runtime 'secrets.xml'
    $values=Import-Clixml $secretFile
    $sqlText="BEGIN; SET LOCAL log_statement='none'; SET LOCAL log_duration=off; SET LOCAL log_min_duration_statement=-1; SET LOCAL log_min_duration_sample=-1; SET LOCAL log_min_error_statement='panic'; "
    foreach ($role in $targets) {
        $bytes=New-Object byte[] 48; $rng=[Security.Cryptography.RandomNumberGenerator]::Create();$rng.GetBytes($bytes);$rng.Dispose()
        $plain=[Convert]::ToBase64String($bytes)
        $prefix=$role.Substring(8).ToUpperInvariant()
        $values["${prefix}_DB_PASSWORD"]=ConvertTo-SecureString $plain -AsPlainText -Force
        $verifier=ScramVerifier $plain
        $sqlText+="ALTER ROLE $role PASSWORD '$verifier'; "
    }
    # Save encrypted recovery material before committing, then publish it as active config only on success.
    $pending=Join-Path $runtime "pending-passwords-$id.xml"
    $values | Export-Clixml $pending
    $sqlText+='COMMIT;'
    [void](Sql $sqlText)
    Copy-Item -LiteralPath $secretFile -Destination (Join-Path $runtime "secrets-before-$id.xml")
    Copy-Item -LiteralPath $pending -Destination $secretFile -Force
    'PASSWORDS_RESET: seven unique generated passwords committed; encrypted application configuration updated.' | Set-Content $status
    foreach ($role in $targets) {
        $prefix=$role.Substring(8).ToUpperInvariant()
        $env:PGUSER=$role;$env:PGDATABASE=$role
        $env:PGPASSWORD=[Net.NetworkCredential]::new('',$values["${prefix}_DB_PASSWORD"]).Password
        if ((Sql 'SELECT 1;') -ne '1') { throw "Connectivity check failed for $role" }
    }
    'CONNECTIVITY_VERIFIED: all seven database roles connect successfully. Starting services.' | Set-Content $status
} catch {
    "STOPPED: $($_.Exception.Message)" | Set-Content $status
    throw
} finally {
    Remove-Item Env:PGPASSWORD,Env:PGUSER,Env:PGDATABASE -ErrorAction SilentlyContinue
    Remove-Variable credential,plain,verifier,sqlText,values -ErrorAction SilentlyContinue
}
& (Join-Path $PSScriptRoot 'Start-Local.ps1')
Start-Sleep -Seconds 35
& (Join-Path $PSScriptRoot 'Test-Demo.ps1')
