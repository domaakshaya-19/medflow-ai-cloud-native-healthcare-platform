# Creates synthetic records through normal authenticated APIs. Does not delete records.
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Initialize-SecureShell.ps1')
$root=Split-Path $PSScriptRoot -Parent
$runtime=Join-Path $root 'local-runtime'
$results=New-Object System.Collections.Generic.List[string]
$results.Add("Verification started UTC: $([DateTime]::UtcNow.ToString('o'))")
function Api($method,$path,$body=$null,$token=$null) {
    $params=@{Uri="http://localhost:8080$path";Method=$method;ContentType='application/json';TimeoutSec=30;ErrorAction='Stop'}
    if ($body) { $params.Body=ConvertTo-Json -InputObject $body -Depth 10 -Compress }
    if ($token) { $params.Headers=@{Authorization="Bearer $token"} }
    try { $data=Invoke-RestMethod @params }
    catch { throw "API verification failed: $method $path. Inspect local service logs privately; response and credentials are not printed." }
    $results.Add("PASS $method $path")
    return $data
}
try {
    $ports=& (Join-Path $PSScriptRoot 'Get-LocalServicePorts.ps1')
    foreach ($port in @($ports.Values | Where-Object {$_ -ne 5173})) {
        $h=Invoke-RestMethod "http://localhost:$port/actuator/health" -TimeoutSec 5
        if ($h.status -ne 'UP') { throw "Unhealthy port $port" }
        $results.Add("PASS health $port UP")
    }
    $registry=Invoke-RestMethod 'http://localhost:8761/eureka/apps' -Headers @{Accept='application/json'}
    foreach ($name in 'AUTH-SERVICE','PATIENT-SERVICE','DOCTOR-SERVICE','APPOINTMENT-SERVICE','MEDICAL-RECORDS-SERVICE','LAB-SERVICE','PRESCRIPTION-SERVICE','API-GATEWAY') {
        $entry=@($registry.applications.application | Where-Object name -eq $name)
        if (!$entry -or @($entry.instance | Where-Object status -eq 'UP').Count -ne 1) { throw "Expected one UP Eureka registration for $name. Wait for discovery convergence and retry." }
        $results.Add("PASS Eureka $name")
    }
    foreach ($path in '/api/auth/me','/api/patients/me','/api/doctors/me','/api/appointments/me','/api/medical-records/me','/api/lab/orders/me','/api/prescriptions/me') {
        $status=0
        try { $null=Invoke-WebRequest "http://localhost:8080$path" -UseBasicParsing -TimeoutSec 10 }
        catch { if ($_.Exception.Response) { $status=[int]$_.Exception.Response.StatusCode } }
        if ($status -ne 401) { throw "Expected unauthenticated 401 for $path; got $status" }
        $results.Add("PASS unauthenticated $path 401")
    }
    $secure=Import-Clixml (Join-Path $runtime 'secrets.xml')
    $adminPassword=[System.Net.NetworkCredential]::new('',$secure.ADMIN_BOOTSTRAP_PASSWORD).Password
    $admin=Api POST '/api/auth/login' @{email='admin@example.test';password=$adminPassword}
    $tag=[Guid]::NewGuid().ToString('N').Substring(0,10)
    $bytes=New-Object byte[] 24; $rng=[Security.Cryptography.RandomNumberGenerator]::Create(); $rng.GetBytes($bytes); $rng.Dispose()
    $password='Demo!Aa1'+[Convert]::ToBase64String($bytes)
    $patientEmail="patient-$tag@example.test"; $doctorEmail="doctor-$tag@example.test"
    # Persist only encrypted passwords for private demo reuse; never persist tokens.
    @{PatientEmail=$patientEmail;DoctorEmail=$doctorEmail;Password=(ConvertTo-SecureString $password -AsPlainText -Force)} | Export-Clixml (Join-Path $runtime "demo-$tag.xml")
    $patientUser=Api POST '/api/auth/register' @{fullName='Synthetic Demo Patient';email=$patientEmail;password=$password}
    $patient=Api POST '/api/auth/login' @{email=$patientEmail;password=$password}
    $null=Api GET '/api/auth/me' $null $patient.accessToken
    $profile=Api POST '/api/patients' @{firstName='Synthetic';lastName='Patient';dateOfBirth='2000-01-01';gender='OTHER';phone='+1 202 555 0100';email=$patientEmail;address='Synthetic demo address';bloodGroup='O+'} $patient.accessToken
    $ownProfile=Api GET '/api/patients/me' $null $patient.accessToken
    if ($ownProfile.id -ne $profile.id) { throw 'Patient profile read-back mismatch.' }
    $staff=Api POST '/api/auth/staff' @{fullName='Synthetic Demo Doctor';email=$doctorEmail;password=$password;role='DOCTOR'} $admin.accessToken
    $doctor=Api POST '/api/auth/login' @{email=$doctorEmail;password=$password}
    $dp=Api POST '/api/doctors' @{firstName='Synthetic';lastName='Doctor';specialization='General Medicine';qualification='Demo qualification';licenseNumber="DEMO-$tag";phone='+1 202 555 0101';email=$doctorEmail;department='Demo';experienceYears=1;consultationFee=100;available=$true} $doctor.accessToken
    $directory=@(Api GET '/api/doctors/directory' $null $patient.accessToken)
    if ($dp.id -notin $directory.id) { throw 'Synthetic doctor not present in directory.' }
    $appointment=Api POST '/api/appointments' @{doctorId=$dp.id;appointmentDate=(Get-Date).AddDays(1).ToString('yyyy-MM-dd');appointmentTime='10:00:00';reason='Synthetic academic demonstration';notes='No real clinical data'} $patient.accessToken
    $myAppointments=@(Api GET '/api/appointments/me' $null $patient.accessToken)
    if ($appointment.id -notin $myAppointments.id) { throw 'Booked appointment missing from patient list.' }
    $null=Api PUT "/api/appointments/$($appointment.id)" @{status='CONFIRMED'} $doctor.accessToken
    $null=Api PUT "/api/appointments/$($appointment.id)" @{status='IN_PROGRESS'} $doctor.accessToken
    $reference=@{patientId=$profile.id;doctorId=$dp.id;appointmentId=$appointment.id}
    $record=$reference.Clone();$record.diagnosis='Synthetic demonstration only';$record.visitDate=(Get-Date).ToString('yyyy-MM-dd');$record.notes='Not clinical advice'
    $createdRecord=Api POST '/api/medical-records' $record $doctor.accessToken
    $myRecords=@(Api GET '/api/medical-records/me' $null $patient.accessToken)
    if ($createdRecord.id -notin $myRecords.id) { throw 'Created medical record missing from patient list.' }
    $orderBody=$reference.Clone();$orderBody.testName='Synthetic lab test';$orderBody.instructions='Academic demo only'
    $order=Api POST '/api/lab/orders' $orderBody $doctor.accessToken
    $result=Api POST '/api/lab/results' @{orderId=$order.id;resultSummary='Synthetic normal result';details='No real specimen';abnormal=$false;resultDate=[DateTime]::UtcNow.ToString('o')} $doctor.accessToken
    $myOrders=@(Api GET '/api/lab/orders/me' $null $patient.accessToken)
    $myResults=@(Api GET '/api/lab/results/me' $null $patient.accessToken)
    if ($order.id -notin $myOrders.id -or $result.id -notin $myResults.id) { throw 'Lab order/result read-back mismatch.' }
    $rx=$reference.Clone();$rx.diagnosis='Synthetic demonstration only';$rx.notes='Not a real prescription';$rx.medicines=@(@{medicineName='Synthetic demo item';dosage='Demo only';frequency='Demo only';duration='Demo only';instructions='Do not administer'})
    $createdRx=Api POST '/api/prescriptions' $rx $doctor.accessToken
    $myPrescriptions=@(Api GET '/api/prescriptions/me' $null $patient.accessToken)
    if ($createdRx.id -notin $myPrescriptions.id) { throw 'Created prescription missing from patient list.' }
    $null=Invoke-WebRequest 'http://127.0.0.1:5173/' -UseBasicParsing -TimeoutSec 5
    $results.Add('PASS frontend HTTP 200 (browser interaction requires separate verification)')
    $results.Add("Synthetic records retained; encrypted demo account file: demo-$tag.xml")
    Write-Host 'All requested API smoke flows passed. Browser interaction is not covered by this script.'
} catch {
    $results.Add("INCOMPLETE: $($_.Exception.Message)")
    throw
} finally {
    $results | Set-Content (Join-Path $runtime 'verification.txt')
    Remove-Variable adminPassword,password,secure,admin,patient,doctor -ErrorAction SilentlyContinue
}
