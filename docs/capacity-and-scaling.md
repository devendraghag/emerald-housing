# Capacity and scaling plan

This plan records the limits already configured in the repo and a repeatable way to measure capacity. It does not claim a requests-per-second capacity: no representative load test or production SLO has been recorded yet.

## Current scaling controls

| Workload | Min replicas | Max replicas | CPU HPA target | Per-pod requests | Per-pod limits |
| --- | ---: | ---: | ---: | --- | --- |
| API Gateway | 2 | 4 | 70% of requested CPU | 100m CPU, 192Mi memory | 750m CPU, 512Mi memory |
| Member Service | 2 | 3 | 70% of requested CPU | 100m CPU, 256Mi memory | 750m CPU, 640Mi memory |
| Society Service | 2 | 4 | 70% of requested CPU | 100m CPU, 256Mi memory | 750m CPU, 640Mi memory |

All three Deployments now declare two initial replicas to match their HPA floors. HPAs scale up immediately when CPU exceeds the target; scale-down waits five minutes and removes at most one pod per minute. CPU utilization is measured against requests, so changing requests changes the HPA trigger. CPU throttling and memory pressure still need dashboard alerts.

The HPA resources require the Kubernetes resource metrics API (`metrics.k8s.io`). Verify it before relying on autoscaling:

```powershell
kubectl get apiservice v1beta1.metrics.k8s.io
kubectl top nodes
kubectl top pods -n emerald-housing
kubectl get hpa -n emerald-housing
```

If the metrics API is missing or HPA reports `<unknown>`, install/configure a metrics provider for the target cluster before evaluating scale behavior. The repo's kind creation script currently creates the cluster only; it does not install a metrics provider.

## Resource envelope from current settings

At the HPA maximum, the three apps request about **1.1 CPU cores and 2.5 GiB memory** in total. During simultaneous rolling updates, `maxSurge: 1` allows up to 4 Member, 5 Society, and 5 Gateway pods; the combined requests rise to about **1.4 CPU cores and 3.2 GiB memory**. The corresponding configured limits can reach **10.5 CPU cores and 8.1 GiB memory** during that surge. These are scheduling envelopes, not measured utilization. Ensure cluster allocatable capacity leaves room for Kubernetes, Redis, ingress, observability, and temporary rollout surge.

## Database and request concurrency ceilings

The configured Hikari writer pool is 8 connections per pod; the optional reader pool is 4 per pod. HPA maximums therefore imply these theoretical pool ceilings:

| Database client | Normal at HPA max | During one surge pod |
| --- | ---: | ---: |
| Member DB writer pool | 24 | 32 |
| Member DB reader pool (when enabled) | 12 | 16 |
| Society DB writer pool | 32 | 40 |
| Society DB reader pool (when enabled) | 16 | 20 |

If all four pools share one PostgreSQL server, simultaneous maximum scale plus rolling surge can permit up to **72 writer and 36 reader connections**. The pools grow on demand, so this is an upper bound; compare it with the database's `max_connections` after reserving capacity for migrations, administration, and other clients. Before raising HPA maximums or pool sizes, calculate `replica ceiling × pool size` and recheck the database budget.

Member Service allows up to 100 Tomcat request threads per pod and 500 connections; its Society lookup bulkhead allows 12 concurrent calls per pod. At the Member HPA maximum, that is up to 300 request threads and 36 concurrent Society validations. Society Service sets a 500-connection Tomcat cap and 50-connection accept queue per pod; its worker-thread count remains at the Spring Boot default. These are concurrency caps, not throughput estimates.

## Capacity measurement procedure

Run this in an isolated staging or local cluster with production-like resource limits, representative data, the same database topology, and the metrics API installed. Do not use real user records or production fault injection.

1. Record the app image tags, replica counts, HPA state, database connection limits, and test dataset size.
2. Exercise key paths separately: Society flat/floor reads, paginated list reads, Member reads, and a controlled membership assignment that performs the Member-to-Society lookup.
3. Ramp offered load gradually to the environment's chosen target. Hold each step long enough for CPU, HPA, caches, and connection pools to settle; include a burst and a scale-down observation.
4. Record throughput and p50/p95/p99 latency, HTTP error/timeout rates, CPU usage/throttling, memory/GC, Tomcat busy threads, Hikari active/pending/timeouts, database CPU and active connections, Redis latency, and HPA desired/current replicas.
5. Repeat at min replicas and near max replicas. Confirm that the HPA reaches its cap without exhausting database or node capacity and that scale-down does not cause a latency spike.
6. Set explicit SLO and saturation thresholds from product needs, then keep the observed maximum sustainable load below the first resource or dependency limit with operational headroom.

Do not increase replica counts based on CPU alone if the measured bottleneck is PostgreSQL connections, slow queries, Redis, or synchronous service calls. Fix the bottleneck or cap incoming work first. Record the result, test command/profile, dataset shape, and charts with the release so a future change can be compared against the same baseline.

## Scaling decisions not set by this repository

- Target request rate, latency/error SLOs, and required headroom.
- Metrics provider installation and alert thresholds.
- Production database `max_connections`, replica count, and reserved operator capacity.
- Whether HPA maxima should increase after load testing.
- Load test tooling/profile and representative data volume.
