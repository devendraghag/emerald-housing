$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$composeFile = Join-Path $repoRoot 'docker-compose.e2e.yml'
$project = 'emerald-e2e-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$composeArgs = @('compose', '-p', $project, '-f', $composeFile)
$testExitCode = 1

& docker version --format '{{.Server.Version}}' *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker Engine is not available. Start Docker Desktop, then run this command again.' }
& docker compose version *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose is not available. Install/enable the Docker Compose plugin, then run this command again.' }

try {
    & docker @composeArgs up --build --abort-on-container-exit --exit-code-from e2e-test
    $testExitCode = $LASTEXITCODE
} finally {
    & docker @composeArgs down --volumes --remove-orphans
    if ($LASTEXITCODE -ne 0 -and $testExitCode -eq 0) { $testExitCode = $LASTEXITCODE }
}

exit $testExitCode
