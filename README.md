# Emerald Housing Society

The project documentation and learning guide, including Phases 1-8, is maintained in [index.html](index.html). This README adds the Step 9 implementation notes below.

## Step 9 - Resilience and Fault Tolerance

Step 9 adds bounded failure handling around Member Service's read-only Society flat lookup, and durable idempotency for member creation. This is source/configuration work only; the services, builds and Kubernetes configuration have not been run as part of this change.

### Member-to-Society lookup

`services/member-service/src/main/java/com/emerald/member/client/SocietyClient.java` wraps each flat GET in a Resilience4j circuit breaker and semaphore bulkhead, with a bounded retry around the physical calls. It preserves the typed flat response, internal service credential and correlation ID. Membership assignment still checks the member locally, calls Society to validate the flat, then writes through `MembershipWriter`. The remote wait occurs outside the database transaction.

Settings are in `services/member-service/src/main/resources/application.yml` and are passed to Kubernetes from `kubernetes/config/application-config.yaml`:

| Setting | Default | Meaning |
|---|---:|---|
| `SOCIETY_SERVICE_CONNECT_TIMEOUT` | `2s` | Maximum time to establish a Society HTTP connection. |
| `SOCIETY_SERVICE_READ_TIMEOUT` | `3s` | Maximum wait for one Society response. |
| `SOCIETY_RETRY_MAX_ATTEMPTS` | `3` | Three total attempts: the first request plus at most two retries. |
| `SOCIETY_RETRY_INITIAL_INTERVAL` | `200ms` | Initial exponential retry delay. |
| `SOCIETY_RETRY_MULTIPLIER` | `2.0` | Multiplies the delay for later attempts. |
| `SOCIETY_RETRY_JITTER` | `0.2` | Adds random variation to avoid synchronized retries. |
| `SOCIETY_RETRY_MAX_INTERVAL` | `1s` | Caps an individual retry delay. |
| `SOCIETY_CB_WINDOW_SIZE` | `10` | Number of recent physical HTTP attempts considered by the breaker. |
| `SOCIETY_CB_MINIMUM_CALLS` | `5` | Minimum calls before failure rates can open the breaker. |
| `SOCIETY_CB_FAILURE_RATE` | `50` | Failure percentage that opens the breaker. |
| `SOCIETY_CB_SLOW_CALL_RATE` | `60` | Slow-call percentage that can open it. |
| `SOCIETY_CB_SLOW_CALL_DURATION` | `2s` | Duration treated as a slow call. |
| `SOCIETY_CB_OPEN_WAIT` | `15s` | Time before the breaker permits recovery trials. |
| `SOCIETY_CB_HALF_OPEN_CALLS` | `2` | Maximum trial calls in half-open state. |
| `SOCIETY_BULKHEAD_MAX_CONCURRENT` | `12` | Concurrent flat lookups allowed per Member Service process. |
| `SOCIETY_BULKHEAD_MAX_WAIT` | `0ms` | Excess lookups fail promptly instead of queueing. |

Only transient transport failures and remote 502/503/504 responses are retried. Flat-not-found (404), bad requests, conflicts, malformed responses and other permanent failures are not retried. Technical dependency failures are counted by the circuit breaker; a normal 404 does not open it. When OPEN, or when the bulkhead is full, assignment returns a controlled 503 and does not save a membership. A Society response timeout maps to 504. A failed flat lookup is never treated as a valid flat.

Call order: local member check -> circuit-breaker and bulkhead permit -> bounded Society GET -> at most two transient retries with capped exponential randomized delay -> active-flat check -> local membership transaction. Retry is outside the circuit breaker, so the breaker records each physical HTTP attempt. Each Member Pod has its own circuit-breaker state; it is not shared through Redis. Member reads and other operations that use only `member_db` remain independent of Society availability.

### Gateway rate limits, health and shutdown

The WebFlux Gateway uses Spring Cloud Gateway's Redis rate limiter for protected Member and Society routes. The authenticated key comes from the security principal; client-supplied identity headers are not used. Login attempts use a shared Redis bucket and fail closed with 503 if the limiter cannot be reached; an exhausted bucket returns 429 with `Retry-After`. Default login settings are 2 new tokens/second with burst 5; Kubernetes overrides these to 1/second with burst 3. Member route defaults are 10/second with burst 20; Society route defaults are 20/second with burst 40. These are learning defaults and should be tuned to measured traffic. The unauthenticated login bucket is shared, so all unauthenticated login attempts use the same bucket.

Gateway response timeout defaults to 28 seconds; NGINX upstream read/send timeouts are 32 seconds to leave outer-layer overhead around the Member retry budget and the backend's session-validation call. These are client/proxy wait limits, not a single end-to-end deadline. Member Service has bounded Tomcat threads, connections and accept queue, a zero-wait dependency bulkhead, graceful shutdown with a 20-second phase timeout, and existing Kubernetes readiness/liveness probes. Member readiness checks its local database; Society availability does not control Member liveness. Actuator metrics are exposed by the applications, while Gateway metrics require ADMIN authorization. Resilience4j circuit-breaker, retry and bulkhead meters are bound to Micrometer. The Society client records physical lookup duration and timeout counts; Gateway records 429 responses by fixed route name and login limiter errors. Do not use member IDs, session IDs or idempotency keys as metric labels.

### Durable member-creation idempotency

`POST /api/v1/members` accepts an optional `Idempotency-Key` (1 to 128 letters, digits, period, underscore, colon or hyphen). Without it, the existing create behavior remains. With it, Member Service scopes the key to the authenticated admin and `CREATE_MEMBER`, fingerprints the normalized request with SHA-256, and stores the response with the create in one `member_db` transaction. Repeating the same key and payload returns the stored logical response and `Idempotency-Replayed: true`; using that key with different input returns 409. A unique database constraint coordinates concurrent submissions, so a repeated request cannot create two members.

The new migration is `services/member-service/src/main/resources/db/migration/V3__create_idempotency_records.sql`; earlier migrations are unchanged. Records expire after 24 hours by default (`IDEMPOTENCY_RECORD_TTL`). Expired rows are removed opportunistically during a later idempotent create; no background cleanup job is configured. The stored response can contain member contact fields, so keep the retention short, restrict database access, and account for this data in database backup/retention policy.

Member creation and flat assignment are not automatically retried. Idempotency makes a repeated create safe only when the client deliberately resubmits the same key and payload; it does not justify a Gateway retry. Assignment is a mutation with mandatory live flat validation, so any dependency failure fails the request without persisting membership.

### Manual verification guide (not run here)

1. Send a valid assignment while Society is available; confirm the membership is stored in `member_db`.
2. Stop or make Society unavailable, then send an assignment. Expect a controlled 503/504 and verify that no membership row was added. Do not retry the POST automatically.
3. While Society remains unavailable, retrieve a member through the Gateway. That local database operation should still work.
4. Repeat safe flat GET attempts during an outage. After the configured threshold, that Member Pod's breaker should OPEN and reject lookups without contacting Society. After 15 seconds, limited HALF_OPEN trials can check recovery; successful trials close the breaker.
5. To observe bulkhead rejection, create controlled concurrent assignment traffic above 12 calls for one Member Pod. Excess validation work should fail promptly with 503; do not use production traffic for this exercise.
6. Send a short burst of Society/member API requests and login attempts through Gateway; inspect 429 and `Retry-After`. Shared rate limiting requires Redis to be reachable.
7. Create a member with an `Idempotency-Key`, repeat the exact request, then reuse that key with a changed email. Expect the same member response on replay and 409 for the changed payload.
8. Inspect protected Actuator metrics for Resilience4j circuit-breaker, retry, bulkhead, rate-limit and idempotency measurements. Circuit state can differ between Member Pods.

The combined collection `postman/Emerald-Housing-Phase1.postman_collection.json` now includes manual idempotency examples and outage/bulkhead descriptions. The local environment has example idempotency variables; replace the mobile number with an unused value before creating a member. No Postman scripts were added.

### Verification status

Static source/configuration review only. No test files were created, edited or executed. No Maven/Gradle commands, Docker builds, Kubernetes commands, application starts or Postman collection runs were performed. Runtime behavior, dependency compatibility, rate limiting, Flyway V3 execution, failure timing, and metrics still require manual verification in the configured environment.
