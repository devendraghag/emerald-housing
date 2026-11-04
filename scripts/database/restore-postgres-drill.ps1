param(
    [Parameter(Mandatory = $true)][string]$BackupDirectory,
    [Parameter(Mandatory = $true)][ValidatePattern('^restore_[A-Za-z0-9_]{1,40}$')][string]$TargetSuffix,
    [Parameter(Mandatory = $true)][string]$HostName,
    [int]$Port = 5432,
    [string]$Createdb = 'createdb',
    [string]$PgRestore = 'pg_restore'
)

$ErrorActionPreference = 'Stop'

function Get-RequiredEnvironmentValue([string]$Name) {
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) { throw "Required environment variable $Name is not set." }
    return $value
}

function Invoke-Checked([string]$Executable, [string[]]$Arguments, [string]$Operation) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Operation failed with exit code $LASTEXITCODE." }
}

function Restore-Database([string]$SourceDatabase, [string]$Username, [string]$Password, [string]$DumpFile, [string]$TargetDatabase) {
    $previousPassword = $env:PGPASSWORD
    try {
        $adminUsername = Get-RequiredEnvironmentValue 'PG_ADMIN_USERNAME'
        $env:PGPASSWORD = Get-RequiredEnvironmentValue 'PG_ADMIN_PASSWORD'
        Invoke-Checked $Createdb @('--no-password', '--host', $HostName, '--port', "$Port", '--username', $adminUsername,
            "--owner=$Username", $TargetDatabase) "Creation of isolated target $TargetDatabase"
        $env:PGPASSWORD = $Password
        Invoke-Checked $PgRestore @('--no-password', '--exit-on-error', '--no-owner', '--no-privileges', '--host', $HostName,
            '--port', "$Port", '--username', $Username, '--dbname', $TargetDatabase, $DumpFile) "Restore of $SourceDatabase to $TargetDatabase"
    } finally {
        if ($null -eq $previousPassword) { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
        else { $env:PGPASSWORD = $previousPassword }
    }
}

$resolvedBackup = (Resolve-Path -LiteralPath $BackupDirectory).Path
$manifestPath = Join-Path $resolvedBackup 'manifest.json'
if (!(Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw 'Backup manifest.json is missing.' }
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json

$societyEntry = $manifest.backups | Where-Object database -eq 'society_db' | Select-Object -First 1
$memberEntry = $manifest.backups | Where-Object database -eq 'member_db' | Select-Object -First 1
if (!$societyEntry -or !$memberEntry) { throw 'Manifest must include both society_db and member_db archives.' }

foreach ($entry in @($societyEntry, $memberEntry)) {
    if ([IO.Path]::GetFileName($entry.file) -ne $entry.file) { throw 'Manifest archive paths must be filenames in the backup directory.' }
    $archivePath = Join-Path $resolvedBackup $entry.file
    if (!(Test-Path -LiteralPath $archivePath -PathType Leaf)) { throw "Backup archive $($entry.file) is missing." }
    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $archivePath).Hash.ToLowerInvariant()
    if ($actualHash -ne $entry.sha256.ToLowerInvariant()) { throw "Checksum mismatch for $($entry.file)." }
}

$societyUsername = Get-RequiredEnvironmentValue 'SOCIETY_DB_USERNAME'
$societyPassword = Get-RequiredEnvironmentValue 'SOCIETY_DB_PASSWORD'
$memberUsername = Get-RequiredEnvironmentValue 'MEMBER_DB_USERNAME'
$memberPassword = Get-RequiredEnvironmentValue 'MEMBER_DB_PASSWORD'
$null = Get-RequiredEnvironmentValue 'PG_ADMIN_USERNAME'
$null = Get-RequiredEnvironmentValue 'PG_ADMIN_PASSWORD'
$societyTarget = "society_db_$TargetSuffix"
$memberTarget = "member_db_$TargetSuffix"
if ($societyTarget -eq 'society_db' -or $memberTarget -eq 'member_db') { throw 'Restore targets must be isolated databases.' }

Restore-Database 'society_db' $societyUsername $societyPassword (Join-Path $resolvedBackup $societyEntry.file) $societyTarget
Restore-Database 'member_db' $memberUsername $memberPassword (Join-Path $resolvedBackup $memberEntry.file) $memberTarget
Write-Output "Restore drill completed. Validate $societyTarget and $memberTarget before changing any application connection strings."
