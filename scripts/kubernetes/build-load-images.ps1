param(
    [switch]$IncludeGateway,
    [string]$ServiceTag = 'step4-audit',
    [string]$GatewayTag = 'step6-audit'
)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$cluster = 'emerald-housing'
$images = @(
    @{ Name = "emerald/society-service:$ServiceTag"; Context = 'services\society-service' },
    @{ Name = "emerald/member-service:$ServiceTag"; Context = 'services\member-service' }
)
if ($IncludeGateway) {
    $images += @{ Name = "emerald/api-gateway:$GatewayTag"; Context = 'infrastructure\api-gateway' }
}
foreach ($image in $images) {
    $context = Join-Path $repoRoot $image.Context
    & docker build --tag $image.Name --file (Join-Path $context 'Dockerfile') $context
    if ($LASTEXITCODE -ne 0) { throw "Docker image build failed: $($image.Name)" }
    & kind load docker-image $image.Name --name $cluster
    if ($LASTEXITCODE -ne 0) { throw "Loading image into kind failed: $($image.Name)" }
}
