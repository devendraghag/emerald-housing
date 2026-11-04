# Deployment and database recovery runbook

This runbook covers the Kubernetes workloads and the two PostgreSQL databases. The Kubernetes manifests deploy Society Service, Member Service, Gateway, and Redis; they do **not** provision PostgreSQL. Confirm the database provider's own backups and point-in-time recovery (PITR) before relying on this runbook for production.

## What the repo already configures

- App Deployments use `RollingUpdate`, `maxUnavailable: 0`, `maxSurge: 1`, and keep three prior revisions.
- Startup, readiness, and liveness probes use Actuator health endpoints. Readiness includes required local dependencies; liveness stays process-focused so a downstream outage does not trigger restart loops.
- App pods allow 30 seconds for graceful shutdown. Spring's shutdown phase timeout is 20 seconds.
- `scripts/kubernetes/deploy-option-b.ps1` waits for each rollout and reports workload state. A failed deployment can be reverted with `kubectl rollout undo`.
- PostgreSQL databases are external to these manifests. Flyway upgrades schemas during application startup, so retain a tested backup before deploying a migration.

## Create a portable logical backup

Install PostgreSQL client tools (`pg_dump` and `pg_restore`) on the operator machine. Use a protected terminal or secret manager to set these environment variables; do not put passwords in command-line arguments or commit them:

```powershell
$env:SOCIETY_DB_USERNAME = '<society database user>'
$env:SOCIETY_DB_PASSWORD = '<society database password>'
$env:MEMBER_DB_USERNAME = '<member database user>'
$env:MEMBER_DB_PASSWORD = '<member database password>'
$env:PG_ADMIN_USERNAME = '<temporary restore operator with CREATEDB>'
$env:PG_ADMIN_PASSWORD = '<restore operator password>'
```

Create and verify custom-format archives plus a SHA-256 manifest:

```powershell
.scriptsdatabaseackup-postgres.ps1 -HostName '<database host>' -Port 5432
```

The script writes into `%USERPROFILE%\EmeraldHousingBackups\<UTC timestamp>`. Copy that folder to encrypted, access-controlled storage outside the cluster and verify that the off-host copy is readable. A successful local archive check is not proof that the off-host copy or provider PITR works.

Run backups at the frequency required by the target recovery point objective (RPO). This script is an on-demand logical backup; it does not schedule itself, create a cloud snapshot, or provide point-in-time recovery. For managed PostgreSQL, enable provider automated backups/PITR and retention separately.

The two database dumps run one after the other, so they are not a single cross-database snapshot. If a recovery point must represent one coordinated application state, pause writes during the backup window or use a provider-level coordinated recovery process and record the chosen recovery time.

## Practice a restore into isolated databases

Run the drill against a disposable PostgreSQL instance or approved nonproduction database host with roles matching the configured app users. The script verifies manifest checksums, creates two new databases with a `restore_...` suffix, and refuses to target the live database names:

```powershell
.\scripts\database\restore-postgres-drill.ps1 `
  -BackupDirectory "$env:USERPROFILE\EmeraldHousingBackups\<UTC timestamp>" `
  -TargetSuffix "restore_$(Get-Date -Format yyyyMMddHHmmss)" `
  -HostName '<isolated database host>' -Port 5432
```

Check representative records, schema/Flyway state, constraints, indexes, and application readiness against those restored databases. Do not repoint production services to the drill targets. Drop drill databases only after validation and only on the isolated host.

For an actual database disaster, restore provider PITR/snapshot or the verified archives to a recovery instance, validate both databases, then update the protected database connection configuration and roll out the backend services. Keep the old instance intact until the recovered services pass smoke checks. The final cutover and old-instance retirement depend on the chosen database provider and are intentionally operator-controlled.

## Roll back an unhealthy application rollout

Inspect the affected Deployment and events first:

```powershell
kubectl --context docker-desktop rollout status deployment/member-service -n emerald-housing --timeout=300s
kubectl --context docker-desktop describe deployment/member-service -n emerald-housing
kubectl --context docker-desktop get pods -n emerald-housing
```

Roll back only the affected Deployment, then wait for the previous revision to become healthy:

```powershell
kubectl --context docker-desktop rollout undo deployment/member-service -n emerald-housing
kubectl --context docker-desktop rollout status deployment/member-service -n emerald-housing --timeout=300s
```

Repeat for `society-service` or `api-gateway` when those are the failed workload. Check service health and a harmless read through Gateway. Application rollback does not reverse Flyway migrations; migrations must remain backward-compatible with the previous app revision or be repaired with a forward migration. Database recovery is a separate process.

## Recovery targets and remaining operator decisions

Set explicit RPO/RTO targets for the environment, choose database backup retention and off-site location, and schedule regular restore drills. Record the last successful backup, archive location, restore duration, and validation evidence. Kubernetes rollback protects application availability; only database backups/PITR protect persistent records.
