param(
    [string]$BaseUrl = 'https://emerald.local',
    [string]$Namespace = 'emerald-housing',
    [int]$Samples = 100,
    [int]$ExpectedCanaryPercent = 10,
    [int]$TolerancePercentagePoints = 6
)

$ErrorActionPreference = 'Stop'

if ($Samples -lt 1 -or $ExpectedCanaryPercent -lt 0 -or $ExpectedCanaryPercent -gt 100 -or $TolerancePercentagePoints -lt 0) {
    throw 'Samples must be positive; expected traffic and tolerance must be valid percentages.'
}

if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) { throw 'kubectl is required.' }
if (-not (Get-Command curl.exe -ErrorAction SilentlyContinue)) { throw 'curl.exe is required.' }

kubectl wait --namespace $Namespace --for=condition=Available deployment/api-gateway deployment/api-gateway-canary --timeout=180s
if ($LASTEXITCODE -ne 0) { throw 'The primary or canary Gateway Deployment is not available.' }

$routeJson = kubectl get httproute emerald-api-to-spring-gateway --namespace $Namespace -o json
if ($LASTEXITCODE -ne 0) { throw 'Could not read the canary HTTPRoute.' }
$route = $routeJson | ConvertFrom-Json
$backends = @($route.spec.rules[0].backendRefs)
$primary = @($backends | Where-Object { $_.name -eq 'api-gateway' })
$canary = @($backends | Where-Object { $_.name -eq 'api-gateway-canary' })
if ($primary.Count -ne 1 -or $canary.Count -ne 1) { throw 'The HTTPRoute must direct traffic to both primary and canary Gateways.' }
if ([int]$primary[0].weight + [int]$canary[0].weight -ne 100) { throw 'HTTPRoute backend weights must total 100.' }
if ([Math]::Abs([int]$canary[0].weight - $ExpectedCanaryPercent) -gt 0) {
    throw "Configured canary weight is $($canary[0].weight)%; expected $ExpectedCanaryPercent%."
}

$conditions = @($route.status.parents | ForEach-Object { $_.conditions })
foreach ($conditionName in @('Accepted', 'ResolvedRefs')) {
    if (-not ($conditions | Where-Object { $_.type -eq $conditionName -and $_.status -eq 'True' })) {
        throw "HTTPRoute does not report $conditionName=True. Check the Gateway controller and route status."
    }
}

$uri = $BaseUrl.TrimEnd('/') + '/api/v1/auth/csrf'
$canaryHits = 0
for ($i = 0; $i -lt $Samples; $i++) {
    $response = & curl.exe --insecure --silent --show-error --dump-header - --output NUL --write-out "`nSTATUS:%{http_code}" $uri 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Canary probe $($i + 1) failed: $($response -join ' ')" }
    $raw = $response -join "`n"
    if ($raw -notmatch 'STATUS:200') { throw "Canary probe $($i + 1) did not return HTTP 200: $raw" }
    if ($raw -match '(?im)^X-Gateway-Instance:\s*api-gateway-canary\s*$') { $canaryHits++ }
    elseif ($raw -notmatch '(?im)^X-Gateway-Instance:\s*api-gateway\s*$') {
        throw "Probe response did not identify either Gateway version: $raw"
    }
}

$observedPercent = [Math]::Round(100.0 * $canaryHits / $Samples, 1)
if ([Math]::Abs($observedPercent - $ExpectedCanaryPercent) -gt $TolerancePercentagePoints) {
    throw "Observed canary traffic was $observedPercent% ($canaryHits/$Samples), outside the plus/minus $TolerancePercentagePoints point tolerance."
}

[pscustomobject]@{
    Namespace = $Namespace
    Route = $route.metadata.name
    PrimaryWeight = [int]$primary[0].weight
    CanaryWeight = [int]$canary[0].weight
    Samples = $Samples
    CanaryHits = $canaryHits
    ObservedCanaryPercent = $observedPercent
    RouteAccepted = $true
    BothDeploymentsReady = $true
}
