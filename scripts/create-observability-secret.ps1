param([string]$Namespace = 'emerald-housing')

$ErrorActionPreference = 'Stop'
$securePassword = Read-Host 'Grafana administrator password' -AsSecureString
$passwordPointer = [IntPtr]::Zero
$passwordBytes = $null

try {
    $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    if ([string]::IsNullOrWhiteSpace($plainPassword) -or $plainPassword.Length -lt 16) {
        throw 'Use a non-empty Grafana password with at least 16 characters.'
    }

    $passwordBytes = [Text.Encoding]::UTF8.GetBytes($plainPassword)
    $encodedPassword = [Convert]::ToBase64String($passwordBytes)
    $secret = @{
        apiVersion = 'v1'
        kind = 'Secret'
        metadata = @{ name = 'emerald-observability-credentials'; namespace = $Namespace }
        type = 'Opaque'
        data = @{ 'grafana-admin-password' = $encodedPassword }
    }
    $json = ConvertTo-Json -InputObject $secret -Depth 5 -Compress
    $json | kubectl apply -f -
    if ($LASTEXITCODE -ne 0) { throw 'Could not apply the observability Secret. Confirm the namespace exists and kubectl is connected.' }
    Write-Host 'Grafana administrator Secret created or updated.'
}
finally {
    if ($passwordBytes) { [Array]::Clear($passwordBytes, 0, $passwordBytes.Length) }
    if ($passwordPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer) }
    if ($securePassword) { $securePassword.Dispose() }
    Remove-Variable plainPassword, encodedPassword, json, secret, passwordBytes -ErrorAction SilentlyContinue
}
