$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Initialize-SecureShell.ps1')
$root=Split-Path $PSScriptRoot -Parent
Set-Location $root
$runtime=Join-Path $root 'local-runtime'
$status=Join-Path $runtime 'gemini-verification.txt'
try{
    $stage='key entry and format validation'
    $candidate=Read-Host 'Gemini API key (hidden; only a synthetic aggregate demonstration will be sent)' -AsSecureString
    $plain=& (Join-Path $PSScriptRoot 'Normalize-GeminiKey.ps1') ([Net.NetworkCredential]::new('',$candidate).Password)
    $stage='encrypted key storage'
    $values=Import-Clixml (Join-Path $runtime 'secrets.xml')
    $values.GEMINI_API_KEY=ConvertTo-SecureString $plain -AsPlainText -Force
    $pending=Join-Path $runtime 'gemini-config-pending.xml'
    $values | Export-Clixml $pending
    [IO.File]::Replace($pending,(Join-Path $runtime 'secrets.xml'),(Join-Path $runtime ('secrets-before-gemini-'+[DateTime]::UtcNow.ToString('yyyyMMddHHmmss')+'.xml')))
    Remove-Variable plain,candidate,values
    'Key stored encrypted; restarting only AI Service.' | Set-Content $status
    $stage='AI service restart'
    & (Join-Path $PSScriptRoot 'Stop-Local.ps1') -Services @('ai-service')
    Start-Sleep -Seconds 3
    $stillUp=$false
    try{$r=Invoke-WebRequest 'http://localhost:8093/actuator/health' -UseBasicParsing -TimeoutSec 2;$stillUp=($r.StatusCode -eq 200)}catch{}
    if($stillUp){throw 'AI process could not be stopped with verified identity. Run this script in a host PowerShell terminal; no other process was stopped.'}
    & (Join-Path $PSScriptRoot 'Start-Local.ps1') -Services @('ai-service')
    Start-Sleep -Seconds 10
    $values=Import-Clixml (Join-Path $runtime 'secrets.xml')
    $stage='local authentication'
    $body=@{email='admin@example.test';password=[Net.NetworkCredential]::new('',$values.ADMIN_BOOTSTRAP_PASSWORD).Password} | ConvertTo-Json
    $session=Invoke-RestMethod 'http://localhost:8080/api/auth/login' -Method Post -ContentType 'application/json' -Body $body
    $request=@{agent='PATIENT';tool='doctor.directory';arguments=@{};invokeModel=$true;syntheticDemo=$true;consentToModel=$true} | ConvertTo-Json
    $stage='Gemini provider invocation'
    $result=Invoke-RestMethod 'http://localhost:8080/api/ai/requests' -Method Post -ContentType 'application/json' -Headers @{Authorization="Bearer $($session.accessToken)"} -Body $request -TimeoutSec 35
    if($result.mode -ne 'GEMINI' -or $result.modelStatus -ne 'INVOKED' -or [string]::IsNullOrWhiteSpace($result.explanation)){throw 'Model invocation was not verified.'}
    "PASS real Gemini invocation UTC $([DateTime]::UtcNow.ToString('o')); agent PATIENT; authorized tool doctor.directory; only aggregate context transmitted; model text returned. No medical verification claimed." | Set-Content $status
    Write-Host 'Real Gemini invocation verified. The key, token, and generated text were not printed.'
}catch{
    $httpStatus='not reached'
    if($_.Exception.Response){$httpStatus=[string][int]$_.Exception.Response.StatusCode}
    "BLOCKED at $stage. Exception type: $($_.Exception.GetType().Name); local HTTP status: $httpStatus. Key and response details suppressed; no mock response substituted." | Set-Content $status
    throw 'Gemini verification did not complete. Inspect configuration/provider access privately; no credential values were printed.'
}finally{Remove-Variable plain,candidate,values,body,session,result -ErrorAction SilentlyContinue}
