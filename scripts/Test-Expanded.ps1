# Uses real local APIs and synthetic records. No provider mocks and no real payments/messages.
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Initialize-SecureShell.ps1')
$root=Split-Path $PSScriptRoot -Parent
$runtime=Join-Path $root 'local-runtime'
$results=New-Object System.Collections.Generic.List[string]
$results.Add("Expanded verification UTC: $([DateTime]::UtcNow.ToString('o'))")
function Api($method,$path,$body=$null,$token=$null,[int]$expected=200){
 $params=@{Uri="http://localhost:8080$path";Method=$method;ContentType='application/json';UseBasicParsing=$true;TimeoutSec=30;ErrorAction='Stop'}
 if($body){$params.Body=ConvertTo-Json -InputObject $body -Depth 12 -Compress};if($token){$params.Headers=@{Authorization="Bearer $token"}}
 $response=$null;$status=0
 try{$response=Invoke-WebRequest @params;$status=[int]$response.StatusCode}catch{if($_.Exception.Response){$status=[int]$_.Exception.Response.StatusCode}}
 if($status -ne $expected){throw "HTTP check failed: $method $path expected $expected, received $status. Response details suppressed."}
 $results.Add("PASS $method $path HTTP $status")
 if($response -and $response.Content){$parsed=ConvertFrom-Json -InputObject $response.Content;foreach($row in $parsed){Write-Output $row}}
}
function RandomPassword{ $bytes=New-Object byte[] 24;$rng=[Security.Cryptography.RandomNumberGenerator]::Create();$rng.GetBytes($bytes);$rng.Dispose();return 'Demo!Aa1'+[Convert]::ToBase64String($bytes) }
function Assistant($agent,$tool,$token,$arguments=@{}){
 $r=Api POST '/api/ai/requests' @{agent=$agent;tool=$tool;arguments=$arguments;invokeModel=$false} $token
 if($r.agent -ne $agent -or $r.mode -ne 'CONTROLLED_TOOL' -or $r.modelStatus -ne 'NOT_INVOKED'){throw "Assistant mode/agent mismatch for $agent"}
 $results.Add("PASS agent $agent authorized tool $tool without fabricated model output")
 return $r
}
try{
 $ports=& (Join-Path $PSScriptRoot 'Get-LocalServicePorts.ps1')
 $registry=Invoke-RestMethod 'http://localhost:8761/eureka/apps' -Headers @{Accept='application/json'}
 foreach($item in $ports.GetEnumerator()){
  if($item.Key -in @('discovery-server','frontend')){continue}
  $entry=@($registry.applications.application | Where-Object name -eq $item.Key.ToUpperInvariant())
  $up=@($entry.instance | Where-Object status -eq 'UP')
  if($up.Count -ne 1 -or [int]$up[0].port.'$' -ne [int]$item.Value){throw "Discovery registration mismatch for $($item.Key)"}
  $results.Add("PASS Eureka $($item.Key) port $($item.Value)")
 }
 $secure=Import-Clixml (Join-Path $runtime 'secrets.xml')
 $admin=Api POST '/api/auth/login' @{email='admin@example.test';password=[Net.NetworkCredential]::new('',$secure.ADMIN_BOOTSTRAP_PASSWORD).Password}
 $base=Get-ChildItem $runtime -Filter 'demo-*.xml' | Where-Object Name -notlike 'demo-business-*' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
 if(!$base){throw 'Run Test-Demo.ps1 first.'}
 $demo=Import-Clixml $base.FullName
 $pass=[Net.NetworkCredential]::new('',$demo.Password).Password
 $patient=Api POST '/api/auth/login' @{email=$demo.PatientEmail;password=$pass}
 $doctor=Api POST '/api/auth/login' @{email=$demo.DoctorEmail;password=$pass}
 $profile=Api GET '/api/patients/me' $null $patient.accessToken
 $appointment=@(Api GET '/api/appointments/me' $null $patient.accessToken)[0]
 $rx=@(Api GET '/api/prescriptions/me' $null $patient.accessToken)[0]
 $record=@(Api GET '/api/medical-records/me' $null $patient.accessToken)[0]
 $tag=[guid]::NewGuid().ToString('N').Substring(0,10)
 $staff=@{};$accounts=@{}
 foreach($role in 'PHARMACIST','BILLING_STAFF','EMERGENCY_STAFF','LAB_STAFF','NURSE','RECEPTIONIST'){
  $email=$role.ToLowerInvariant()+"-$tag@example.test";$password=RandomPassword
  $null=Api POST '/api/auth/staff' @{fullName="Synthetic $role";email=$email;password=$password;role=$role} $admin.accessToken 201
  $staff[$role]=Api POST '/api/auth/login' @{email=$email;password=$password}
  $accounts[$role]=@{email=$email;password=(ConvertTo-SecureString $password -AsPlainText -Force)}
 }
 $otherPassword=RandomPassword;$otherEmail="other-$tag@example.test"
 $null=Api POST '/api/auth/register' @{fullName='Synthetic Other Patient';email=$otherEmail;password=$otherPassword} $null 201
 $other=Api POST '/api/auth/login' @{email=$otherEmail;password=$otherPassword}
 $null=Api POST '/api/patients' @{firstName='Synthetic';lastName='Other';dateOfBirth='2000-01-01';phone='+1 202 555 0130';email=$otherEmail} $other.accessToken 201
 $availability=Api GET "/api/appointments/availability?doctorId=$($appointment.doctorId)&date=$($appointment.appointmentDate)" $null $patient.accessToken
 if('10:00' -in $availability.availableStartTimes -or $availability.workingHoursConfigured){throw 'Availability must exclude the booked start time and disclose missing working-hours model'}
 $null=Api POST '/api/appointments' @{doctorId=$appointment.doctorId;appointmentDate=$appointment.appointmentDate;appointmentTime='10:00:00';reason='Synthetic conflicting start time'} $other.accessToken 409
 $reschedule=Api POST '/api/appointments' @{doctorId=$appointment.doctorId;appointmentDate=$appointment.appointmentDate;appointmentTime='12:00:00';reason='Synthetic rescheduling and cancellation test'} $other.accessToken 201
 $null=Api PUT "/api/appointments/$($reschedule.id)" @{appointmentTime='10:00:00'} $other.accessToken 409
 $null=Api PUT "/api/appointments/$($reschedule.id)" @{appointmentTime='13:00:00'} $other.accessToken
 $null=Api DELETE "/api/appointments/$($reschedule.id)" $null $other.accessToken 204
 $cancelled=Api GET "/api/appointments/$($reschedule.id)" $null $other.accessToken
 if($cancelled.status -ne 'CANCELLED'){throw 'Cancellation must preserve the appointment with CANCELLED status'}
 $accounts.OtherPatient=@{email=$otherEmail;password=(ConvertTo-SecureString $otherPassword -AsPlainText -Force)}
 $accounts | Export-Clixml (Join-Path $runtime "demo-business-$tag.xml")
 foreach($path in @("/api/patients/$($profile.id)","/api/appointments/$($appointment.id)","/api/medical-records/$($record.id)","/api/prescriptions/$($rx.id)")){$null=Api GET $path $null $other.accessToken 403}
 $pharmacist=$staff.PHARMACIST.accessToken;$billing=$staff.BILLING_STAFF.accessToken;$emergency=$staff.EMERGENCY_STAFF.accessToken;$lab=$staff.LAB_STAFF.accessToken
 $catalog=@(Api GET '/api/pharmacy/medications' $null $pharmacist)
 $medication=$catalog | Where-Object name -eq 'Synthetic demo item' | Select-Object -First 1
 if(!$medication){$medication=Api POST '/api/pharmacy/medications' @{name='Synthetic demo item';stock=100;unit='synthetic units'} $pharmacist 201}
 if([int]$medication.stock -lt 2){$medication=Api PUT "/api/pharmacy/medications/$($medication.id)/stock" @{quantity=10} $pharmacist}
 $null=Api POST '/api/pharmacy/medications' @{name="Forbidden-$tag";stock=10;unit='units'} $patient.accessToken 403
 $null=Api POST '/api/pharmacy/dispensations' @{prescriptionId=$rx.id;medicationId=$medication.id;quantity=1;confirmed=$false} $pharmacist 400
 $dispensation=Api POST '/api/pharmacy/dispensations' @{prescriptionId=$rx.id;medicationId=$medication.id;quantity=1;confirmed=$true} $pharmacist 201
 $null=Api POST '/api/pharmacy/dispensations' @{prescriptionId=$rx.id;medicationId=$medication.id;quantity=1;confirmed=$true} $pharmacist 409
 $ownDispensations=@(Api GET '/api/pharmacy/dispensations/me' $null $patient.accessToken)
 if($dispensation.id -notin $ownDispensations.id){throw 'Dispensing read-back mismatch'}
 $food=Api GET '/api/nutrition/foods' $null $patient.accessToken
 if(@($food.foods).Count -lt 2 -or !($food.foods[0].source -like 'https://fdc.nal.usda.gov/*')){throw 'Food source/data check failed'}
 $null=Api PUT '/api/nutrition/profile' @{goal='VARIETY';notes='Synthetic general wellness demonstration'} $patient.accessToken
 $recommendations=Api GET '/api/nutrition/recommendations' $null $patient.accessToken
 if(!$recommendations.method -or !$recommendations.limitation){throw 'Rule transparency missing'}
 $null=Api GET '/api/nutrition/profile' $null $other.accessToken 404
 $today=(Get-Date).ToString('yyyy-MM-dd');$progress=@(Api GET '/api/nutrition/progress' $null $patient.accessToken)
 if(!($progress | Where-Object entryDate -eq $today)){$null=Api POST '/api/nutrition/progress' @{entryDate=$today;fruitServings=1;notes='Synthetic self-entered observation, not a dietary target'} $patient.accessToken 201}
 $invoice=Api POST '/api/billing/invoices' @{patientUserId=$patient.user.id;currency='INR';items=@(@{description='Synthetic academic consultation';quantity=2;unitPrice=100.25})} $billing 201
 if([decimal]$invoice.total -ne 200.50){throw 'Server invoice total mismatch'}
 $null=Api GET "/api/billing/invoices/$($invoice.id)" $null $other.accessToken 403
 $null=Api POST "/api/billing/invoices/$($invoice.id)/demo-payment" @{confirmed=$false} $patient.accessToken 400
 $paid=Api POST "/api/billing/invoices/$($invoice.id)/demo-payment" @{confirmed=$true} $patient.accessToken
 if($paid.paymentStatus -ne 'PAID_DEMO'){throw 'Synthetic payment status mismatch'}
 $request=Api POST '/api/emergency' @{location='Synthetic demonstration lobby';summary='Academic administrative request; no real emergency'} $patient.accessToken 201
 $null=Api GET "/api/emergency/$($request.id)" $null $other.accessToken 403
 foreach($status in 'ASSIGNED','IN_PROGRESS','CLOSED'){$request=Api PUT "/api/emergency/$($request.id)" @{status=$status;administrativePriority='ADMIN_STANDARD';assignToSelf=($status -eq 'ASSIGNED')} $emergency}
 if(@($request.history).Count -ne 4){throw 'Emergency history mismatch'}
 $null=Api POST '/api/notifications/events/appointment' @{appointmentId=$appointment.id} $patient.accessToken
 $null=Api POST '/api/notifications/events/appointment' @{appointmentId=$appointment.id} $patient.accessToken
 $notifications=@(Api GET '/api/notifications' $null $patient.accessToken)
 $booked=@($notifications | Where-Object {$_.appointmentId -eq $appointment.id -and $_.kind -eq 'APPOINTMENT'})
 if($booked.Count -ne 1){throw 'Appointment notification deduplication failed'}
 $null=Api PUT "/api/notifications/$($booked[0].id)/read" @{} $other.accessToken 403
 $null=Api PUT "/api/notifications/$($booked[0].id)/read" @{} $patient.accessToken
 $null=Api PUT '/api/notifications/preferences' @{enabled=$true;reminders=$true} $patient.accessToken
 $order=Api POST '/api/lab/orders' @{patientId=$profile.id;doctorId=$appointment.doctorId;appointmentId=$appointment.id;testName="Expanded synthetic lab test $tag";instructions='Academic demonstration only'} $doctor.accessToken 201
 $null=Api PUT "/api/lab/orders/$($order.id)" @{status='COLLECTED'} $lab
 $null=Api POST '/api/lab/results' @{orderId=$order.id;resultSummary='Synthetic demonstration result';details='No real specimen';abnormal=$false;resultDate=[DateTime]::UtcNow.ToString('o')} $lab 201
 $null=Api GET "/api/lab/orders/$($order.id)" $null $other.accessToken 403
 foreach($role in 'NURSE','RECEPTIONIST'){$summary=Api GET '/api/appointments/operations/summary' $null $staff[$role].accessToken;if($null -eq $summary.totalAppointments -or $summary.patientId){throw 'Operations aggregate contract failed'};$null=Api GET '/api/appointments' $null $staff[$role].accessToken 403}
 $null=Assistant PATIENT 'patient.records' $patient.accessToken
 $null=Assistant DOCTOR 'doctor.records' $doctor.accessToken
 $null=Assistant EMERGENCY 'emergency.mine' $patient.accessToken
 $null=Assistant PHARMACY 'pharmacy.prescription' $pharmacist @{prescriptionId=$rx.id}
 $null=Assistant BILLING 'billing.invoice' $patient.accessToken @{invoiceId=$invoice.id}
 $null=Assistant OPERATIONS 'operations.summary' $staff.NURSE.accessToken
 $null=Assistant NUTRITION 'nutrition.foods' $patient.accessToken
 $null=Api POST '/api/ai/requests' @{agent='DOCTOR';tool='doctor.records';arguments=@{}} $patient.accessToken 403
 $null=Api POST '/api/ai/requests' @{agent='PATIENT';tool='shell.exec';arguments=@{}} $patient.accessToken 400
 $null=Api POST '/api/ai/requests' @{agent='PATIENT';tool='patient.records';arguments=@{patientId=$other.user.id}} $patient.accessToken 400
 $null=Api POST '/api/ai/requests' @{agent='BILLING';tool='billing.invoice';arguments=@{invoiceId=$invoice.id}} $other.accessToken 403
 $cap=Api GET '/api/ai/capabilities' $null $patient.accessToken
 if(!$cap.modelConfigured){$null=Api POST '/api/ai/requests' @{agent='PATIENT';tool='patient.records';invokeModel=$true;syntheticDemo=$true;consentToModel=$true} $patient.accessToken 503;$results.Add('BLOCKED live Gemini: no key configured. No mock response claimed as a model call.')}
 else{$results.Add('Live Gemini configured but not invoked by this read-only smoke script; explicit provider verification is separate.')}
 Write-Host 'Expanded synthetic workflows, seven agents, discovery, and ownership/role denial checks passed.'
}catch{$results.Add("INCOMPLETE: $($_.Exception.Message)");throw}
finally{$results | Set-Content (Join-Path $runtime 'expanded-verification.txt');Remove-Variable secure,demo,pass,admin,patient,doctor,staff,accounts,password,otherPassword,other -ErrorAction SilentlyContinue}
