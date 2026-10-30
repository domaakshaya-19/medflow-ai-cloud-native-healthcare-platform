$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$results=@()
foreach ($port in 8085,8086,8087) {
    $connections=@(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
    foreach ($connection in $connections) {
        $process=Get-CimInstance Win32_Process -Filter "ProcessId=$($connection.OwningProcess)"
        $results += [pscustomobject]@{port=$port;pid=$connection.OwningProcess;name=$process.Name;path=$process.ExecutablePath;isThisMedFlow=($process.CommandLine -like "*$root*");isMedicalRecords=($process.CommandLine -like '*medical-records-service*');isLab=($process.CommandLine -like '*lab-service*');isPrescription=($process.CommandLine -like '*prescription-service*')}
    }
}
ConvertTo-Json -InputObject @($results) -Depth 5 | Set-Content (Join-Path $root 'local-runtime\port-owners.json')
