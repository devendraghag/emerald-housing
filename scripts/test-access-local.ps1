param([string]$PostgresBin = 'C:/Program Files/PostgreSQL/18/bin')
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$testDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ('emerald-access-test-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testDirectory | Out-Null
$dataDirectory = Join-Path $testDirectory 'data'
$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
$listener.Start()
$testPort = $listener.LocalEndpoint.Port
$listener.Stop()
& (Join-Path $PostgresBin 'initdb.exe') -D $dataDirectory -U postgres --auth=trust --encoding=UTF8 --locale=C
if ($LASTEXITCODE -ne 0) { throw 'Temporary PostgreSQL initialization failed.' }
$started = $false
try {
    & (Join-Path $PostgresBin 'pg_ctl.exe') -D $dataDirectory -l (Join-Path $testDirectory 'postgres.log') -o "-p $testPort -h 127.0.0.1" -w -t 30 start
    if ($LASTEXITCODE -ne 0) { throw 'Temporary PostgreSQL startup failed.' }
    $started = $true
    & (Join-Path $repoRoot 'services/member-service/mvnw.cmd') -B -f (Join-Path $repoRoot 'services/member-service/pom.xml') '-Dtest=AccessFamilyIntegrationTest' "-Daccess.test.jdbc-url=jdbc:postgresql://127.0.0.1:$testPort/postgres" test
    if ($LASTEXITCODE -ne 0) { throw 'Access and family integration tests failed.' }
} finally {
    if ($started) { & (Join-Path $PostgresBin 'pg_ctl.exe') -D $dataDirectory -m fast -w -t 30 stop }
    Write-Output "Isolated test logs and data: $testDirectory"
}
