param(
    [string]$DatabaseCredentialsFile,
    [switch]$BuildImages,
    [switch]$InfrastructureOnly,
    [switch]$VerifyOnly
)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$clusterContext = 'docker-desktop'
$namespace = 'emerald-housing'
function Invoke-Kube {
    & kubectl --context $clusterContext @args
    if ($LASTEXITCODE -ne 0) { throw "kubectl failed: $($args[0])" }
}
if ($VerifyOnly) {
    foreach ($deployment in @('redis','redis-cache','society-service','member-service','api-gateway')) {
        Invoke-Kube rollout status "deployment/$deployment" -n $namespace --timeout=60s
    }
    Invoke-Kube get 'deployments,pods,services' -n $namespace
    exit
}
function Apply-Secret([string]$Name, [hashtable]$Values) {
    @{apiVersion='v1';kind='Secret';metadata=@{name=$Name;namespace=$namespace};type='Opaque';stringData=$Values} |
        ConvertTo-Json -Depth 8 -Compress | & kubectl --context $clusterContext apply -f -
    if ($LASTEXITCODE -ne 0) { throw "Unable to apply Secret $Name" }
}
# Windows PowerShell 5.1 does not support the static GetBytes(int) overload.
function New-PrivateValue {
    $bytes=New-Object byte[] 32
    $rng=[System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes); [Convert]::ToBase64String($bytes) } finally { $rng.Dispose() }
}
Invoke-Kube get nodes
Invoke-Kube apply -f (Join-Path $repoRoot 'kubernetes/namespace.yaml')
$existingDb = & kubectl --context $clusterContext get secret emerald-db-credentials -n $namespace --ignore-not-found -o name
if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect database Secret.' }
if ($DatabaseCredentialsFile) {
    $credentials = Get-Content -LiteralPath $DatabaseCredentialsFile -Raw | ConvertFrom-Json
    $dbValues=@{}
    foreach ($key in @('society-username','society-password','member-username','member-password')) {
        $value=$credentials.$key
        if ([string]::IsNullOrWhiteSpace($value)) { throw "Private credentials file must contain $key" }
        $dbValues[$key]=[string]$value
    }
    Apply-Secret 'emerald-db-credentials' $dbValues
} elseif (!$existingDb -and !$InfrastructureOnly) {
    throw 'Database Secret is missing. Supply -DatabaseCredentialsFile with society-username, society-password, member-username and member-password. Existing databases and passwords will not be changed.'
}
$security = & kubectl --context $clusterContext get secret emerald-security-credentials -n $namespace --ignore-not-found -o name
if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect security Secret.' }
if (!$security) {
    Apply-Secret 'emerald-security-credentials' @{
        'admin-username'=('admin-'+[Guid]::NewGuid().ToString('N'))
        'admin-password'=[Guid]::NewGuid().ToString('N')
        'redis-password'=(New-PrivateValue)
        'redis-cache-password'=(New-PrivateValue)
        'gateway-authentication-token'=(New-PrivateValue)
        'session-validation-token'=(New-PrivateValue)
        'member-to-society-token'=(New-PrivateValue)
    }
}
if ($BuildImages) {
    $releaseTag='option-b-'+(Get-Date -Format 'yyyyMMddHHmmss')
    foreach ($build in @(@('society-service','step4-audit','services/society-service'),@('member-service','restclient-fix','services/member-service'),@('api-gateway','step6-audit','infrastructure/api-gateway'))) {
        & docker --context desktop-linux build -t "emerald/$($build[0]):$releaseTag" (Join-Path $repoRoot $build[2])
        if ($LASTEXITCODE -ne 0) { throw "Image build failed for $($build[0])" }
    }
}
Invoke-Kube apply -f (Join-Path $repoRoot 'kubernetes/config/application-config.yaml')
Invoke-Kube apply -f (Join-Path $repoRoot 'kubernetes/redis')
Invoke-Kube apply -f (Join-Path $repoRoot 'kubernetes/redis-cache')
foreach ($deployment in @('redis','redis-cache')) { Invoke-Kube rollout status "deployment/$deployment" -n $namespace --timeout=180s }
if ($InfrastructureOnly) { Write-Output 'Redis infrastructure is ready. Application deployment still requires the database Secret.'; exit }
Invoke-Kube apply -f (Join-Path $repoRoot 'kubernetes/society')
Invoke-Kube apply -f (Join-Path $repoRoot 'kubernetes/member')
Invoke-Kube apply -f (Join-Path $repoRoot 'kubernetes/gateway/config.yaml')
Invoke-Kube apply -f (Join-Path $repoRoot 'kubernetes/gateway/deployment.yaml')
Invoke-Kube apply -f (Join-Path $repoRoot 'kubernetes/gateway/service.yaml')
Invoke-Kube set env deployment/api-gateway SESSION_COOKIE_SECURE=false -n $namespace
foreach ($deployment in @('society-service','member-service','api-gateway')) {
    if ($BuildImages) { Invoke-Kube set image "deployment/$deployment" "$deployment=emerald/${deployment}:$releaseTag" -n $namespace }
    else { Invoke-Kube rollout restart "deployment/$deployment" -n $namespace }
    Invoke-Kube rollout status "deployment/$deployment" -n $namespace --timeout=300s
}
Invoke-Kube get 'deployments,pods,services' -n $namespace
Write-Output 'Option B deployed. Forward Gateway: kubectl --context docker-desktop port-forward service/api-gateway 8080:8080 -n emerald-housing'
