$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$cluster = 'emerald-housing'
$clusters = & kind get clusters
if ($LASTEXITCODE -ne 0) { throw 'Unable to query kind clusters. Confirm Docker and kind are installed.' }
if ($clusters -notcontains $cluster) {
    & kind create cluster --name $cluster --config (Join-Path $repoRoot 'kubernetes\kind-config.yaml')
    if ($LASTEXITCODE -ne 0) { throw 'kind cluster creation failed.' }
}
& kubectl cluster-info --context "kind-$cluster"
if ($LASTEXITCODE -ne 0) { throw 'The kind cluster is not reachable through kubectl.' }
