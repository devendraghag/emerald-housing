param(
    [Parameter(Mandatory = $true)][string]$HostName,
    [int]$Port = 5432,
    [string]$OutputDirectory = (Join-Path $env:USERPROFILE 'EmeraldHousingBackups'),
    [string]$PgDump = 'pg_dump',
    [string]$PgRestore = 'pg_restore'
)

$ErrorActionPreference = 'Stop'

function Invoke-Checked([string]$Executable, [string[]]$Arguments, [string]$Operation) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Operation failed with exit code $LASTEXITCODE." }
}

function Get-RequiredEnvironmentValue([string]$Name) {
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) { throw "Required environment variable $Name is not set." }
    return $value
}

function Export-Database([string]$Database, [string]$Username, [string]$Password, [string]$Destination) {
    $previousPassword = $env:PGPASSWORD
    $env:PGPASSWORD = $Password
    try {
        Invoke-Checked $PgDump @('--no-password', '--host', $HostName, '--port', "$Port", '--username', $Username,
            '--format=custom', '--file', $Destination, $Database) "Backup of $Database"
        Invoke-Checked $PgRestore @('--list', $Destination) "Archive verification for $Database"
    } catch {
        Remove-Item -LiteralPath $Destination -Force -ErrorAction SilentlyContinue
        throw
    } finally {
        if ($null -eq $previousPassword) { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
        else { $env:PGPASSWORD = $previousPassword }
    }
}

$societyUsername = Get-RequiredEnvironmentValue 'SOCIETY_DB_USERNAME'
$societyPassword = Get-RequiredEnvironmentValue 'SOCIETY_DB_PASSWORD'
$memberUsername = Get-RequiredEnvironmentValue 'MEMBER_DB_USERNAME'
$memberPassword = Get-RequiredEnvironmentValue 'MEMBER_DB_PASSWORD'

$timestamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$backupDirectory = Join-Path $OutputDirectory $timestamp
New-Item -ItemType Directory -Path $backupDirectory -ErrorAction Stop | Out-Null

$societyFile = Join-Path $backupDirectory 'society_db.dump'
$memberFile = Join-Path $backupDirectory 'member_db.dump'
Export-Database 'society_db' $societyUsername $societyPassword $societyFile
Export-Database 'member_db' $memberUsername $memberPassword $memberFile

$manifest = [ordered]@{
    createdAtUtc = [DateTime]::UtcNow.ToString('o')
    host = $HostName
    port = $Port
    backups = @(
        [ordered]@{ database = 'society_db'; file = 'society_db.dump'; sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $societyFile).Hash.ToLowerInvariant() },
        [ordered]@{ database = 'member_db'; file = 'member_db.dump'; sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $memberFile).Hash.ToLowerInvariant() }
    )
}
$manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $backupDirectory 'manifest.json') -Encoding UTF8
Write-Output "Verified PostgreSQL backups written to $backupDirectory"
