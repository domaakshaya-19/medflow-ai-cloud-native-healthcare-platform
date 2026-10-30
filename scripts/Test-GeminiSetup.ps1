# Tests only a synthetic fixture; never writes to the real encrypted configuration or calls Google.
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Initialize-SecureShell.ps1')
$runtime=Join-Path (Split-Path $PSScriptRoot -Parent) 'local-runtime'
$stage='fixture construction'
try{
 $candidate=ConvertTo-SecureString ('AIzaSyntheticFixture'+[guid]::NewGuid().ToString('N')) -AsPlainText -Force
 $stage='secure string conversion'
 $plain=& (Join-Path $PSScriptRoot 'Normalize-GeminiKey.ps1') ([Net.NetworkCredential]::new('',$candidate).Password)
 $stage='format validation'
 foreach($input in @($plain,('GEMINI_API_KEY="'+$plain+'"'),("'"+$plain+"'"))){$normalized=& (Join-Path $PSScriptRoot 'Normalize-GeminiKey.ps1') $input;if($normalized -ne $plain){throw 'Copied-format normalization failed'}}
 $stage='existing encrypted configuration import'
 $values=Import-Clixml (Join-Path $runtime 'secrets.xml')
 $stage='new optional key assignment'
 $values.GEMINI_API_KEY=ConvertTo-SecureString $plain -AsPlainText -Force
 $stage='isolated encrypted round trip'
 $temp=Join-Path $runtime 'gemini-setup-fixture.xml'
 $values | Export-Clixml $temp
 $replacement=Join-Path $runtime 'gemini-setup-replacement.xml'
 $values | Export-Clixml $replacement
 $backup=Join-Path $runtime 'gemini-setup-fixture-backup.xml'
 [IO.File]::Replace($replacement,$temp,$backup)
 $restored=Import-Clixml $temp
 if([Net.NetworkCredential]::new('',$restored.GEMINI_API_KEY).Password -ne $plain){throw 'Round trip failed'}
 'PASS synthetic Gemini setup validation and encrypted round trip; real configuration untouched.' | Set-Content (Join-Path $runtime 'gemini-setup-diagnostic.txt')
}catch{
 "FAILED stage: $stage; type: $($_.Exception.GetType().FullName); message: $($_.Exception.Message)" | Set-Content (Join-Path $runtime 'gemini-setup-diagnostic.txt')
}finally{
 if($temp -and (Test-Path $temp)){Remove-Item -LiteralPath $temp}
 if($backup -and (Test-Path $backup)){Remove-Item -LiteralPath $backup}
 Remove-Variable plain,candidate,values,restored -ErrorAction SilentlyContinue
}
