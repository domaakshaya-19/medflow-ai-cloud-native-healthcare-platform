param([string[]]$Services=@())
$ErrorActionPreference='Stop'
$file=Join-Path (Split-Path $PSScriptRoot -Parent) 'local-runtime\processes.json'
if (!(Test-Path $file)) { Write-Host 'No tracked processes.'; return }
$records=@()
foreach ($entry in (Get-Content $file -Raw | ConvertFrom-Json)) {
    if ($entry.name) { $records += $entry }
    elseif ($entry.value) { $records += @($entry.value | Where-Object name) }
}
[array]::Reverse($records)
if($Services.Count){$records=@($records | Where-Object {$_.name -in $Services})}
foreach ($record in $records) {
    $p=Get-Process -Id $record.pid -ErrorAction SilentlyContinue
    if (!$p) { continue }
    if ($p.StartTime.ToUniversalTime().Ticks -ne ([DateTime]$record.started).ToUniversalTime().Ticks -or $p.Path -ne $record.path) {
        Write-Warning "Identity changed for $($record.name); leaving process untouched."
        continue
    }
    Stop-Process -Id $p.Id
    Write-Host "Stopped tracked $($record.name)"
}
# PostgreSQL is deliberately left running; no database or volume is deleted.
