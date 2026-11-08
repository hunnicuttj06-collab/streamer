$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
& mvn -B compile exec:java
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
