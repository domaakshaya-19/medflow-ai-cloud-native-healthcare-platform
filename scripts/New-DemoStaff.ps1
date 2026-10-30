param([ValidateSet('DOCTOR','NURSE','RECEPTIONIST','LAB_STAFF','PHARMACIST','BILLING_STAFF','EMERGENCY_STAFF')][string]$Role='DOCTOR')
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Initialize-SecureShell.ps1')
$root=Split-Path $PSScriptRoot -Parent
$email=Read-Host 'New synthetic staff email (must end with @example.test)'
if($email -notmatch '^[A-Za-z0-9._+-]+@example\.test$'){throw 'Use a synthetic @example.test address.'}
$password=Read-Host 'Choose a strong password for this NEW demo account (hidden)' -AsSecureString
$plain=[Net.NetworkCredential]::new('',$password).Password
if($plain.Length -lt 10 -or $plain.Length -gt 72 -or $plain -cnotmatch '^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).+$'){throw 'Password must be 10–72 characters with uppercase/lowercase, digit and symbol.'}
try{
 $values=Import-Clixml (Join-Path $root 'local-runtime/secrets.xml')
 $login=@{email='admin@example.test';password=[Net.NetworkCredential]::new('',$values.ADMIN_BOOTSTRAP_PASSWORD).Password}|ConvertTo-Json
 $session=Invoke-RestMethod 'http://localhost:8080/api/auth/login' -Method Post -ContentType application/json -Body $login
 $body=@{fullName="Synthetic $Role";email=$email;password=$plain;role=$Role}|ConvertTo-Json
 $result=Invoke-RestMethod 'http://localhost:8080/api/auth/staff' -Method Post -ContentType application/json -Headers @{Authorization="Bearer $($session.accessToken)"} -Body $body
 Write-Host "Created synthetic $Role account: $email. Sign in through the frontend with your chosen password. No existing account was changed."
}catch{throw 'Staff creation failed; existing accounts preserved. Check service availability or use a new synthetic email. Credentials were not printed.'}
finally{Remove-Variable password,plain,values,login,session,body,result -ErrorAction SilentlyContinue}
