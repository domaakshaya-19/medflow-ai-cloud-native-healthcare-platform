param([string[]]$OnlyModules=@())
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
Set-Location $root
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$directory='target-build-'+[DateTime]::UtcNow.ToString('yyyyMMddHHmmss')
[xml]$pom=Get-Content (Join-Path $root 'pom.xml')
$selected=@($pom.project.modules.module)
$arguments=@('-B','--fail-at-end','verify',"-Dmedflow.build.directory=$directory")
$log=Join-Path $root 'local-runtime\backend-full-current.log'
if($OnlyModules.Count){
    $valid=@($selected | ForEach-Object {Split-Path $_ -Leaf})
    foreach($name in $OnlyModules){if($name -notin $valid){throw "Unknown module $name"}}
    $selected=@($selected | Where-Object {(Split-Path $_ -Leaf) -in $OnlyModules})
    $arguments+=@('-pl',($selected -join ','),'-am')
    $log=Join-Path $root 'local-runtime\backend-selected-current.log'
}
& mvn.cmd @arguments *> $log
if($LASTEXITCODE -ne 0){throw 'Build/tests failed. Existing runtime artifacts remain selected. Inspect the build log privately.'}
$artifacts=@{}
if($OnlyModules.Count -and (Test-Path 'local-runtime/active-artifacts.json')){$previous=Get-Content 'local-runtime/active-artifacts.json' -Raw | ConvertFrom-Json;foreach($entry in $previous.PSObject.Properties){$artifacts[$entry.Name]=$entry.Value}}
foreach($module in $selected){
    $name=Split-Path $module -Leaf
    $jar=Join-Path $root "$module\$directory\$name-0.1.0-SNAPSHOT.jar"
    if(!(Test-Path $jar)){throw "Missing built artifact: $name"}
    $artifacts[$name]=$jar
}
$artifacts | ConvertTo-Json | Set-Content (Join-Path $root 'local-runtime\active-artifacts.json')
Write-Host 'Requested backend builds and tests passed. Validated replacement artifacts selected for next restart.'
