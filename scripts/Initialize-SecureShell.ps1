# Use only modules shipped with this engine, avoiding inherited PowerShell 5/7 conflicts.
$ErrorActionPreference='Stop'
$engineModules=Join-Path $PSHOME 'Modules'
$env:PSModulePath=$engineModules
if (!(Get-Module Microsoft.PowerShell.Security)) {
    Import-Module (Join-Path $engineModules 'Microsoft.PowerShell.Security\Microsoft.PowerShell.Security.psd1') -ErrorAction Stop
}
