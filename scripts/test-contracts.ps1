$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$wrapper = Join-Path $repoRoot 'services\member-service\mvnw.cmd'
& $wrapper -B -f (Join-Path $repoRoot 'services\member-service\pom.xml') '-Dtest=SocietyConsumerContractTest' test
if ($LASTEXITCODE -ne 0) { throw 'Member consumer contract verification failed.' }
& $wrapper -B -f (Join-Path $repoRoot 'services\society-service\pom.xml') '-Dtest=SocietyProviderContractTest' test
if ($LASTEXITCODE -ne 0) { throw 'Society provider contract verification failed.' }
