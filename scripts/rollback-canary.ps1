param([string]$Namespace = 'emerald-housing')

$ErrorActionPreference = 'Stop'

$patch = '[{"op":"replace","path":"/spec/rules/0/backendRefs","value":[{"name":"api-gateway","port":8080,"weight":100},{"name":"api-gateway-canary","port":8080,"weight":0}]}]'
kubectl patch httproute emerald-api-to-spring-gateway --namespace $Namespace --type=json --patch $patch
if ($LASTEXITCODE -ne 0) { throw 'Could not route all traffic back to the primary Gateway.' }

kubectl scale deployment/api-gateway-canary --namespace $Namespace --replicas=0
if ($LASTEXITCODE -ne 0) { throw 'Traffic is back on the primary, but the canary Deployment could not be scaled down.' }

Write-Host 'Canary traffic is disabled and the canary Deployment is scaled to zero.'
Write-Host 'Reapply the intended Kustomize overlay after correcting the issue; the overlay restores its configured weights.'
