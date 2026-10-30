param([switch]$InfrastructureOnly,[Alias('Services')][string[]]$OnlyServices=@())
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Initialize-SecureShell.ps1')
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
$runtime = Join-Path $root 'local-runtime'
New-Item -ItemType Directory -Force $runtime | Out-Null
$manifest = Join-Path $runtime 'processes.json'
$owned = @()
if (Test-Path $manifest) {
    foreach ($entry in (Get-Content $manifest -Raw | ConvertFrom-Json)) {
        if ($entry.name) { $owned += $entry }
        elseif ($entry.value) { $owned += @($entry.value | Where-Object name) }
    }
}
$java = 'C:\Program Files\Java\jdk-21\bin\java.exe'
if (!(Test-Path $java)) { throw 'Java 21 was not found at the inspected installation path.' }
if (!$InfrastructureOnly) {
    $secureFile = Join-Path $runtime 'secrets.xml'
    if (!(Test-Path $secureFile)) { throw 'Run scripts\Configure-Local.ps1 interactively first.' }
    $secureValues = Import-Clixml $secureFile
    $shared=[System.Net.NetworkCredential]::new('', $secureValues.JWT_SECRET).Password
    if ([Text.Encoding]::UTF8.GetByteCount($shared) -lt 32) { throw 'JWT secret must contain at least 32 UTF-8 bytes.' }
    Remove-Variable shared
}
# Parse data only; never Invoke-Expression a local environment file.
if (Test-Path '.env') {
    foreach ($line in Get-Content '.env') {
        if ($line -match '^\s*([A-Z][A-Z0-9_]*)=(.*)$') {
            $key=$matches[1]; $value=$matches[2].Trim()
            if ($key -notmatch 'PASSWORD|SECRET|KEY' -and $key -notmatch '^(SERVER_PORT|SPRING_)') {
                [Environment]::SetEnvironmentVariable($key,$value,'Process')
            }
        }
    }
}
$env:EUREKA_SERVER_URL='http://localhost:8761/eureka'
$env:FRONTEND_URL='http://localhost:5173'
$env:VITE_API_URL='http://localhost:8080'
$env:SERVER_ADDRESS='127.0.0.1'
$env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE='4'
$env:SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE='1'
$env:ADMIN_BOOTSTRAP_EMAIL='admin@example.test'
$env:ADMIN_BOOTSTRAP_FULL_NAME='Synthetic Demo Administrator'
# Every application is on this host. Explicit loopback discovery also covers services without a local profile file.
$env:SPRING_PROFILES_ACTIVE='local'
$env:EUREKA_INSTANCE_PREFER_IP_ADDRESS='true'
$env:EUREKA_INSTANCE_IP_ADDRESS='127.0.0.1'
$env:EUREKA_INSTANCE_HOSTNAME='localhost'
Remove-Item Env:SERVER_PORT -ErrorAction SilentlyContinue
$services = ,@('discovery-server',8761)
if (!$InfrastructureOnly) {
    foreach ($item in @(@('auth-service',8081),@('patient-service',8082),@('doctor-service',8083),@('appointment-service',8084),@('medical-records-service',8085),@('lab-service',8086),@('prescription-service',8087))) { $services += ,$item }
    foreach($name in 'pharmacy-service','nutrition-service','billing-service','emergency-service','notification-service','ai-service'){$services+=,@($name,0)}
    $db = [Net.Sockets.TcpClient]::new()
    try { $db.Connect('localhost',5432) } finally { $db.Dispose() }
}
$services += ,@('api-gateway',8080)
$services += ,@('frontend',5173)
$ports=& (Join-Path $PSScriptRoot 'Get-LocalServicePorts.ps1')
if($OnlyServices.Count){foreach($requested in $OnlyServices){if(!$ports.Contains($requested)){throw "Unknown project service: $requested"}};$services=@($services | Where-Object {$_[0] -in $OnlyServices})}
foreach ($item in $services) {
    $name=$item[0]; $port=[int]$ports[$name]
    $probe=[Net.Sockets.TcpClient]::new()
    $listener=$false
    try { $probe.Connect('127.0.0.1',$port); $listener=$true } catch {} finally { $probe.Dispose() }
    if ($listener) {
        $known = @($owned | Where-Object { $_.name -eq $name })
        $valid = $false
        foreach ($record in $known) {
            $existing = Get-Process -Id $record.pid -ErrorAction SilentlyContinue
            if ($existing -and $existing.StartTime.ToUniversalTime().Ticks -eq ([DateTime]$record.started).ToUniversalTime().Ticks -and $existing.Path -eq $record.path) { $valid=$true }
        }
        if (!$valid -and !$known.Count) { throw "Port $port is occupied by an untracked process. Inspect it; no process was stopped." }
        $healthUrl=if ($name -eq 'frontend') { "http://127.0.0.1:$port/" } else { "http://localhost:$port/actuator/health" }
        try { $null=Invoke-WebRequest $healthUrl -UseBasicParsing -TimeoutSec 3 } catch { throw "Existing $name on $port is not healthy; inspect it before continuing." }
        if (!$valid) { Write-Warning "Tracked $name is reachable, but process identity is unavailable in this executor. No duplicate will be started." }
        Write-Host "Reusing tracked $name on $port"
        continue
    }
    if ($name -eq 'frontend') {
        $exe=(Get-Command node.exe).Source
        $args=@('"'+(Join-Path $root 'frontend\node_modules\vite\bin\vite.js')+'"','--host','127.0.0.1','--port','5173','--strictPort')
        $cwd=Join-Path $root 'frontend'
    } else {
        $exe=$java; $cwd=$root
        $jar=Join-Path $root "backend\$name\target\$name-0.1.0-SNAPSHOT.jar"
        $artifactFile=Join-Path $runtime 'active-artifacts.json'
        if(Test-Path $artifactFile){
            $artifacts=Get-Content $artifactFile -Raw | ConvertFrom-Json
            if($artifacts.$name){
                $candidate=[IO.Path]::GetFullPath([string]$artifacts.$name)
                if(!$candidate.StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Artifact must be inside this workspace.'}
                $jar=$candidate
            }
        }
        if (!(Test-Path $jar)) { throw "Build missing: $name. Run mvn.cmd verify." }
        $args=@('-Xms64m','-Xmx384m','-jar',('"'+$jar+'"'),"--server.port=$port")
    }
    # Child services receive only their own database password. Never give every service all twelve credentials.
    $secretNames=@('AUTH','PATIENT','DOCTOR','APPOINTMENT','MEDICAL_RECORDS','LAB','PRESCRIPTION','PHARMACY','NUTRITION','BILLING','EMERGENCY','NOTIFICATION') | ForEach-Object {"${_}_DB_PASSWORD"}
    $secretNames+=@('JWT_SECRET','ADMIN_BOOTSTRAP_PASSWORD','GEMINI_API_KEY')
    $savedEnvironment=@{}
    try{
        foreach($key in $secretNames){$savedEnvironment[$key]=[Environment]::GetEnvironmentVariable($key,'Process');[Environment]::SetEnvironmentVariable($key,$null,'Process')}
        if(!$InfrastructureOnly -and $name -notin @('discovery-server','api-gateway','frontend')){
            $prefix=$name.Replace('-service','').Replace('-','_').ToUpperInvariant()
            $required=@('JWT_SECRET')
            if($secureValues.ContainsKey("${prefix}_DB_PASSWORD")){$required+="${prefix}_DB_PASSWORD"}
            if($name -eq 'auth-service'){$required+='ADMIN_BOOTSTRAP_PASSWORD'}
            if($name -eq 'ai-service' -and $secureValues.ContainsKey('GEMINI_API_KEY')){$required+='GEMINI_API_KEY'}
            foreach($key in $required){$plain=[Net.NetworkCredential]::new('',$secureValues[$key]).Password;if([string]::IsNullOrWhiteSpace($plain)){throw "Missing secure setting $key"};[Environment]::SetEnvironmentVariable($key,$plain,'Process')}
        }
        $p=Start-Process -FilePath $exe -ArgumentList $args -WorkingDirectory $cwd -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime "$name.out.log") -RedirectStandardError (Join-Path $runtime "$name.err.log")
    }finally{foreach($key in $secretNames){[Environment]::SetEnvironmentVariable($key,$savedEnvironment[$key],'Process')};Remove-Variable plain -ErrorAction SilentlyContinue}
    $record=[pscustomobject]@{name=$name;pid=$p.Id;port=$port;started=$p.StartTime.ToUniversalTime().ToString('o');path=$exe}
    $owned=@($owned | Where-Object name -ne $name)+@($record)
    ConvertTo-Json -InputObject @($owned) -Depth 6 | Set-Content $manifest
    $url=if ($name -eq 'frontend') { "http://127.0.0.1:$port/" } else { "http://localhost:$port/actuator/health" }
    $ready=$false
    for ($i=0; $i -lt 90; $i++) {
        $p.Refresh()
        if ($p.HasExited) { throw "$name exited. Inspect local-runtime logs privately." }
        try { $r=Invoke-WebRequest $url -UseBasicParsing -TimeoutSec 2; if ($r.StatusCode -eq 200) { $ready=$true; break } } catch {}
        Start-Sleep -Seconds 1
    }
    if (!$ready) { throw "$name did not become healthy. Inspect logs before retrying." }
    Write-Host "$name ready on $port"
}
Write-Host 'Requested components are ready. Frontend: http://localhost:5173'
