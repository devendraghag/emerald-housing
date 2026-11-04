# Test strategy and commands

## Quick map of coverage

- **Unit/service tests:** DTO validation, method security, authorization rules, error mapping, cursor/page handling, and service behavior in each Maven module.
- **PostgreSQL integration tests:** Flyway startup on an empty database, repository behavior, audit/idempotency, member/family workflows, and Society persistence using PostgreSQL Testcontainers.
- **Upgrade tests:** start at Flyway V2, insert representative legacy rows, migrate to the latest version, and verify the rows and new schema survive.
- **Redis integration:** two independent lock objects contend for the same member key against a Redis Testcontainer.
- **Kafka integration:** write a notice event to the outbox, dispatch it to a Kafka Testcontainer, read the event from the broker, and verify the outbox acknowledgement behavior.
- **Service-call resilience:** WireMock tests cover timeouts and transient/permanent failures; Resilience4j tests cover breaker open/recovery and bulkhead rejection.
- **API contract and security checks:** provider/consumer contract tests, Gateway routing/authentication tests, ownership and role tests, and the Postman automated verification folder.

Testcontainers tests skip when Docker is unavailable. A successful Maven command on a machine without Docker therefore does not establish PostgreSQL, Redis, or Kafka container integration coverage.

## Module test commands

Run all tests in one service module:

```powershell
.\services\member-service\mvnw.cmd -B -f .\services\member-service\pom.xml test
.\services\society-service\mvnw.cmd -B -f .\services\society-service\pom.xml test
.\services\member-service\mvnw.cmd -B -f .\infrastructure\api-gateway\pom.xml test
```

## Full end-to-end stack

With Docker Desktop running, this command creates an isolated Compose project containing PostgreSQL, Redis, Kafka, Society Service, Member Service, and API Gateway. It waits for service readiness, runs the collection's **Automated verification** folder through Gateway, then removes only the temporary project and its data volumes:

```powershell
.\scripts\test-e2e.ps1
```

All credentials in this Compose stack are disposable test-only values. Do not reuse them outside the local test stack. The generated Postman verification summary is printed by the runner; production credentials and external services are not used.

## Load profile

`tests/performance/emerald-read-load.js` runs an authenticated, read-only mix of paginated Member and flat-directory GET requests through Gateway. It records request rate, errors, and average/p90/p95/p99 latency. It deliberately has no pass thresholds until the product defines its SLOs.

Run only against a local or approved staging stack, using a dedicated non-production account/session and representative test data. VUs and duration are configurable:

```powershell
$env:BASE_URL = 'http://localhost:8080'
$env:SESSION_COOKIE = 'SESSION=<local-session-value>'
$env:LOAD_VUS = '5'
$env:LOAD_DURATION = '2m'
k6 run --summary-export tests/performance/last-summary.json tests/performance/emerald-read-load.js
Remove-Item Env:SESSION_COOKIE
```

The default profile sends two reads per VU iteration plus one second of think time; the Gateway rate limiter, shared database, or local load generator may become the first limit. Interpret results alongside HPA replicas, CPU/memory, Tomcat threads, Hikari active/pending connections, Redis, and PostgreSQL. Do not label its result a capacity limit until the target SLO, data size, and environment are recorded.

## Security checks

The Java suites already exercise account role refresh, role/scope enforcement, invalid internal credentials, and fail-closed session validation. The Postman automated folder adds request/response checks through Gateway. Keep these checks in the end-to-end run; do not run the authenticated load script against a live production account.
