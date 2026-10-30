param([string]$Value)
$key=$Value.Trim()
if($key -match '^GEMINI_API_KEY\s*='){$key=$key.Substring($key.IndexOf('=')+1).Trim()}
if($key.Length -ge 2 -and (($key[0] -eq [char]34 -and $key[$key.Length-1] -eq [char]34) -or ($key[0] -eq [char]39 -and $key[$key.Length-1] -eq [char]39))){$key=$key.Substring(1,$key.Length-2).Trim()}
# Do not assume a provider-specific alphabet. Reject whitespace/control characters and oversized input.
if($key -notmatch '^[\x21-\x7E]{16,256}$'){throw 'Enter only the API key or a GEMINI_API_KEY assignment; whitespace/control characters are not allowed inside a key.'}
return $key
