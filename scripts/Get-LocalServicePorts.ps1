$ports=[ordered]@{'discovery-server'=8761;'auth-service'=8081;'patient-service'=8082;'doctor-service'=8083;'appointment-service'=8084;'medical-records-service'=18085;'lab-service'=8086;'prescription-service'=8087;'pharmacy-service'=8088;'nutrition-service'=8089;'billing-service'=8090;'emergency-service'=8091;'notification-service'=8092;'ai-service'=8093;'api-gateway'=8080;'frontend'=5173}
$names=@{PATIENT_SERVICE_PORT='patient-service';DOCTOR_SERVICE_PORT='doctor-service';APPOINTMENT_SERVICE_PORT='appointment-service';MEDICAL_RECORDS_SERVICE_PORT='medical-records-service';LAB_SERVICE_PORT='lab-service';PRESCRIPTION_SERVICE_PORT='prescription-service'}
$names.PHARMACY_SERVICE_PORT='pharmacy-service';$names.NUTRITION_SERVICE_PORT='nutrition-service';$names.BILLING_SERVICE_PORT='billing-service';$names.EMERGENCY_SERVICE_PORT='emergency-service';$names.NOTIFICATION_SERVICE_PORT='notification-service';$names.AI_SERVICE_PORT='ai-service'
$file=Join-Path (Split-Path $PSScriptRoot -Parent) '.env'
if(Test-Path $file){
    foreach($line in Get-Content $file){
        if($line -match '^\s*([A-Z_]+_SERVICE_PORT)=(\d+)\s*$' -and $names.ContainsKey($matches[1])){
            $number=[int]$matches[2]
            if($number -lt 1024 -or $number -gt 65535){throw 'Invalid development service port.'}
            $ports[$names[$matches[1]]]=$number
        }
    }
}
if(@($ports.Values | Sort-Object -Unique).Count -ne $ports.Count){throw 'Duplicate application ports in local configuration.'}
return $ports
