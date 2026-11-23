param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('api-gateway', 'member-service', 'society-service', 'ai-service')]
    [string]$Deployment,

    [ValidateRange(15, 900)]
    [int]$DurationSeconds = 120,

    [string]$Namespace = 'emerald-housing',
    [string]$Context,
    [string]$OutputDirectory = (Join-Path (Get-Location) 'artifacts/jfr')
)

$ErrorActionPreference = 'Stop'
$deploymentName = "deployment/$Deployment"
$recordingFile = "emerald-$Deployment.jfr"
$jvmOptions = "-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport -XX:StartFlightRecording=settings=profile,duration=${DurationSeconds}s,filename=/tmp/$recordingFile,dumponexit=true"

function Invoke-Kubectl {
    param([string[]]$KubectlArgs)
    if ($Context) {
        & kubectl --context $Context @KubectlArgs
    } else {
        & kubectl @KubectlArgs
    }
    if ($LASTEXITCODE -ne 0) {
        throw "kubectl failed (exit $LASTEXITCODE): $($KubectlArgs -join ' ')"
    }
}

$profileEnabled = $false
try {
    Write-Host "Enabling a $DurationSeconds-second JFR profile for $Deployment in namespace $Namespace."
    Write-Host 'This changes the Deployment template and restarts its pods. Use a non-production environment unless this restart is explicitly acceptable.'
    New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

    Write-Host ''
    Write-Host 'Prepare the agreed representative workload in another terminal. Start it now and keep it running through the rollout and recording window.'
    Write-Host 'Each replacement JVM records from startup, so load should be active as the pods become ready. Press Enter when the workload is running.'
    [void](Read-Host)

    Invoke-Kubectl @('set', 'env', $deploymentName, "JAVA_TOOL_OPTIONS=$jvmOptions", '-n', $Namespace)
    $profileEnabled = $true
    Invoke-Kubectl @('rollout', 'status', $deploymentName, '-n', $Namespace, '--timeout=180s')

    Write-Host ''
    Write-Host "Waiting $DurationSeconds seconds from rollout completion so every pod has time to finish its bounded recording."
    Start-Sleep -Seconds $DurationSeconds

    $podText = Invoke-Kubectl @('get', 'pods', '-n', $Namespace, '-l', "app=$Deployment", '-o', 'jsonpath={.items[*].metadata.name}')
    $pods = @(([string]$podText).Trim() -split '\s+' | Where-Object { $_ })
    if ($pods.Count -eq 0) {
        throw "No ready pod found for app=$Deployment in namespace $Namespace."
    }

    foreach ($pod in $pods) {
        $destination = Join-Path $OutputDirectory "$pod-$recordingFile"
        Write-Host "Copying $recordingFile from $pod to $destination"
        Invoke-Kubectl @('cp', "$Namespace/$pod`:/tmp/$recordingFile", $destination)
    }

    Write-Host ''
    Write-Host "Profiles saved under $OutputDirectory"
    Write-Host 'Open a recording with: jfr summary <file.jfr> and jfr print --events jdk.ExecutionSample <file.jfr>'
}
finally {
    if ($profileEnabled) {
        Write-Host 'Removing the temporary JAVA_TOOL_OPTIONS override and restoring the image defaults.'
        Invoke-Kubectl @('set', 'env', $deploymentName, 'JAVA_TOOL_OPTIONS-', '-n', $Namespace)
        Invoke-Kubectl @('rollout', 'status', $deploymentName, '-n', $Namespace, '--timeout=180s')
    }
}
