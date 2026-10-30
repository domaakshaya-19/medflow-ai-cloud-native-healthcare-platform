$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
Set-Location $root
$status=Join-Path $root 'local-runtime\restart-status.txt'
try{
    if(!(Test-Path 'local-runtime/active-artifacts.json')){throw 'Run Build-Local.ps1 successfully before restarting.'}
    'Stopping only verified tracked MedFlow processes.' | Set-Content $status
    & (Join-Path $PSScriptRoot 'Stop-Local.ps1')
    Start-Sleep -Seconds 3
    $ports=& (Join-Path $PSScriptRoot 'Get-LocalServicePorts.ps1')
    foreach($item in $ports.GetEnumerator()){
        $url=if($item.Key -eq 'frontend'){"http://127.0.0.1:$($item.Value)/"}else{"http://localhost:$($item.Value)/actuator/health"}
        $reachable=$false
        try{$r=Invoke-WebRequest $url -UseBasicParsing -TimeoutSec 2;$reachable=($r.StatusCode -eq 200)}catch{}
        if($reachable){throw "A listener on $($item.Value) remains. Run this restart script in your own host PowerShell terminal so process identities can be checked. No unverified process will be stopped."}
    }
    'Starting validated artifacts with service-specific credentials.' | Set-Content $status
    & (Join-Path $PSScriptRoot 'Start-Local.ps1')
    'All configured services ready; waiting for discovery.' | Set-Content $status
    Start-Sleep -Seconds 35
    & (Join-Path $PSScriptRoot 'Test-Demo.ps1')
    'COMPLETE: validated artifacts started and core synthetic smoke checks passed.' | Set-Content $status
}catch{"STOPPED: $($_.Exception.Message)" | Set-Content $status;throw}
