# Design recommendations: implementation and operations

This runbook turns the selected microservice recommendations into repeatable repo workflows. Existing application features such as service-owned databases, synchronous assignment validation, Redis caching and membership locks remain in place. Messaging, sharding, a service mesh, and other conditional patterns stay out until a measured requirement calls for them.

## Optional observability overlay

The base `kubernetes/` package remains usable without a telemetry stack. `kubernetes-observability/` layers on Prometheus, Grafana, Loki, Grafana Alloy, and Tempo:

- Prometheus scrapes each annotated Pod, so replicas are reported separately. It retains seven days of local metrics.
- Grafana provisions Prometheus, Loki, and Tempo data sources and an Emerald service dashboard.
- Alloy collects namespace Pod logs through the Kubernetes API and receives OTLP traces from the three Spring applications.
- Tempo and Loki use local ephemeral storage for this learning setup. Their data is lost when their Pods are replaced; production deployments should use persistent or object storage, authentication, retention planning, and resource sizing.
- Spring traces use 10% sampling by default. The observability overlay enables OTLP export. Trace IDs and span IDs are included in application log lines.
- `/actuator/prometheus` is exposed by the apps and permitted through the existing ingress policies only to the Prometheus Pod. NetworkPolicy enforcement depends on the cluster CNI.

Create the Grafana password Secret, then apply the overlay:

```powershell
.\scripts\create-observability-secret.ps1
kubectl apply -k .\kubernetes-observability
kubectl wait -n emerald-housing --for=condition=Available deployment/prometheus deployment/grafana deployment/loki deployment/tempo deployment/grafana-alloy --timeout=240s
kubectl port-forward -n emerald-housing service/grafana 3000:3000
```

Open `http://localhost:3000`, sign in as `admin` with the password used by the script, and open **Emerald Housing Service Overview**. Prometheus and Grafana are reachable only through ClusterIP Services; use `kubectl port-forward` for local access.

Useful checks:

```powershell
kubectl port-forward -n emerald-housing service/prometheus 9090:9090
Invoke-RestMethod http://localhost:9090/api/v1/targets
kubectl logs -n emerald-housing deployment/grafana-alloy
```

Generate a trace with an API call, then search Tempo in Grafana by service name or trace ID. Generate a log by making a request that emits a service log, then search Loki with `{namespace="emerald-housing"}`. Sampling means a trace is not expected for every request.

## Canary validation and rollback

The canary overlay sends 90% of requests to the primary Gateway and 10% to `api-gateway-canary`. Both Gateway versions return `X-Gateway-Instance`; this lets the check distinguish the candidate response. Apply the overlay and load its image before validation. Ensure `emerald.local` resolves to the NGINX Gateway address and its HTTPS certificate is available.

```powershell
kubectl apply -k .\kubernetes-canary
.\scripts\validate-canary.ps1 -BaseUrl 'https://emerald.local' -Samples 100 -ExpectedCanaryPercent 10 -TolerancePercentagePoints 6
```

The validation checks that both Deployments are available, the HTTPRoute is accepted with resolved references, backend weights total 100, the unauthenticated CSRF endpoint returns HTTP 200, and the observed canary share is within the configured tolerance. Sampling checks are statistical; rerun with more requests if a small sample falls outside tolerance. Review HTTP error rate and p95 latency for both `api-gateway` and `api-gateway-canary` in Grafana before changing weights.

To stop canary traffic and scale its Deployment to zero:

```powershell
.\scripts\rollback-canary.ps1
```

This is a manual promotion and rollback workflow. It does not automatically analyze telemetry or promote a candidate. Applying `kubernetes-canary/` again restores the overlay's configured 90/10 split and one canary replica.

## Database capacity and replica operation

Read replicas remain disabled until PostgreSQL has an externally provisioned replica and observed read load justifies it. The dashboard shows per-Pod HTTP volume and Hikari active connections. Compare that with PostgreSQL connection usage and read/write load before changing `MEMBER_DB_REPLICA_ENABLED` or `SOCIETY_DB_REPLICA_ENABLED`.

Before enabling a replica:

1. Provision PostgreSQL streaming replication and a dedicated least-privilege reader account outside this repo.
2. Measure primary connections and read load during representative traffic; compare the result with pool maxima multiplied by the maximum Pod counts.
3. Monitor replication lag on the database platform. On PostgreSQL, `pg_stat_replication` reports primary-side sender state; on a standby, `pg_last_xact_replay_timestamp()` helps estimate replay delay when writes have occurred.
4. Enable the service-specific replica flag and JDBC URL only after the replica is healthy. Keep write and read-after-write decisions on the primary.
5. If lag or replica health breaches the chosen service objective, disable routing and investigate before enabling it again. Application routing currently does not retry a failed replica query against the primary.

## Secrets and deployment access

Application Pods do not need Kubernetes API credentials, so base and canary application Deployments disable automatic ServiceAccount token mounting. Prometheus and Alloy keep scoped, namespace-only ServiceAccounts for Pod discovery and log collection.

Kubernetes Secret values used as environment variables are read when a Pod starts. After rotating database or service credentials, update the Secret and roll the affected Deployment so it reads the new values. Use distinct credentials per service and least-privilege database accounts. A production environment should use an external secret manager and a controlled rotation process; Kubernetes Secret objects alone do not encrypt values at rest unless the cluster is configured to do so.

## Recommendation decisions

| Area | Repo action |
|---|---|
| Database | Keep database-per-service, explicit Hikari pools, constraints, and optional replica routing. Measure load and replica lag before enabling replicas. |
| Communication | Keep the synchronous Member-to-Society assignment check. Add asynchronous messaging only for work that can complete later. |
| Service discovery | Keep Kubernetes Service DNS and endpoint balancing; no separate Eureka server. |
| API Gateway | Keep routing, authentication, and rate limiting. No BFF until client APIs need distinct composition. |
| Resilience | Keep bounded timeouts, retries, circuit breaker, bulkhead, cache fallback, and rate limits; monitor behavior through Prometheus. |
| Consistency | Keep database transactions/constraints, primary reads for freshness-sensitive decisions, Redis membership locking, and row locking. |
| Deployment | Keep rolling deployments and canary routing; use the validation and rollback scripts before candidate promotion. |
| Observability | Optional centralized metrics, logs, and traces are provided by the observability overlay. |
| Security | Keep Gateway and backend authorization, internal credentials, NetworkPolicies, and disable unused Pod API tokens. Rotate secrets with Pod restarts. |
| Caching | Keep Redis cache-aside; measure before adding new cache tiers or changing database read routing. |
| Decomposition | Keep Society and Member ownership boundaries; strengthen domain translation only if their contracts begin evolving independently. |
| Configuration | Keep environment-driven settings, ConfigMaps, and Secret references; adopt a vault/central config service when rotation or coordinated runtime changes require it. |
