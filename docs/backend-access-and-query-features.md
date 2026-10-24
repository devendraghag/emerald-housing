# Audit, permissions, search, cursors and concurrency

The later [dynamic access and family management phase](dynamic-access-and-family-management.md) replaces the fixed role model below and adds the Gateway-hosted `/portal.html` administration interface. The remaining sections describe the original API behaviour and its compatibility contracts.

This change adds backend behavior only. Existing frontend screens remain compatible, but they do not yet expose account management, audit history, server search or conditional updates.

## Scope and roles

The deployment still serves **one society, EMERALD**. This is not a multi-tenant schema. Identities from another society are denied. Existing `ADMIN` accounts continue to work.

| Role | Read society/floors | Read members | Read flats | Change business records | Manage accounts / view audits |
| --- | --- | --- | --- | --- | --- |
| ADMIN / SOCIETY_ADMIN | Yes | All in this deployment | All in EMERALD | Yes | Yes |
| COMMITTEE | Yes | All in this deployment | All in EMERALD | No | No |
| RESIDENT | Yes | Own linked member only | Currently assigned flats only | No | No |

Residents see their own membership history. A flat's membership list is filtered to their own entries. Lists, search results and cursor queries enforce the same access scope as individual-record reads. Service-to-service flat validation retains its narrow existing token-based permission.

Gateway permits the supported roles through its API routes. Each backend independently validates the session and applies method/object permissions. Gateway refreshes account role, enabled state and resident flat assignments from Member during internal session validation. Permission changes therefore affect subsequent backend requests; a disabled or unlinked resident account is rejected. Gateway fails closed when that lookup is unavailable.

### Account APIs (administrators only)

`POST /api/v1/accounts`

```json
{
  "username": "resident-example",
  "password": "REPLACE_WITH_A_PRIVATE_PASSWORD",
  "role": "RESIDENT",
  "societyCode": "EMERALD",
  "memberId": "REPLACE_WITH_EXISTING_MEMBER_UUID"
}
```

Supported roles: `ADMIN`, `SOCIETY_ADMIN`, `COMMITTEE`, `RESIDENT`. Passwords must be 14–72 UTF-8 bytes and are BCrypt-hashed. Resident accounts must link to an existing active member to authenticate. A linked member is identified by an application account mapping, never a browser-supplied identity header. Account responses never include the password or hash.

`GET /api/v1/accounts/{accountId}` returns the account and its `version`.

`PUT /api/v1/accounts/{accountId}/permissions`

```json
{
  "role": "COMMITTEE",
  "societyCode": "EMERALD",
  "memberId": null,
  "enabled": true,
  "version": 0
}
```

Send the version returned by GET. A stale version returns `409 VERSION_CONFLICT`. An administrator cannot change their own permissions using this endpoint, to reduce accidental lockout. Use another administrator. The existing provisioning process still creates ADMIN accounts only and never resets an existing password.

All mutation examples require the existing session cookie and current CSRF token. Login, logout and `/api/v1/auth/me` URLs are unchanged. Re-login after rolling out the new principal/session representation.

## Audit trail

Each service has its own `audit_events` table. Member records member, membership and account/permission changes. Society records flat changes. Audit inserts use the same transaction as the business write: either both persist or both roll back. A failed audit insert fails the business write. Replayed idempotent creates do not produce another creation event.

Events include a sequence, actor account ID/name, action, entity type/ID, before/after JSON snapshots, UTC timestamp and correlation ID. Passwords, hashes, cookies and internal tokens are not part of audit snapshots. Snapshots of member changes do contain personal record details, so only administrators can retrieve them. There is no public update/delete audit API. This is application-level auditing, not tamper-proof logging of arbitrary SQL run by a database administrator. Retention/archival is an operational policy, not an automatic deletion task in this change.

```text
GET /api/v1/members/audit?entityType=MEMBER&entityId={memberId}&limit=50
GET /api/v1/society/audit?entityType=FLAT&entityId={flatId}&limit=50
```

Use `before={sequence}` from the last returned event to retrieve older events. Limit is 1–100. Entity filters are optional. These endpoints route through the existing Gateway member/society routes.

## Server-side search and filters

```text
GET /api/v1/members/page?search=asha&status=ACTIVE&page=0&size=20
GET /api/v1/flats/page?search=A-1&status=ACTIVE&floorNumber=1&page=0&size=20
```

Member search matches name, mobile or email. Flat search matches flat number. Search is case-insensitive, literal substring matching, capped at 120 characters. `%` and `_` are treated literally. Allowed statuses are ACTIVE and INACTIVE. Existing paging and sort validation still applies; the response format is unchanged. Filtering and authorization happen in the database before paging/counting. Substring search may need trigram/full-text indexing at larger measured volumes; the new cursor indexes do not make arbitrary substring searches index-only.

## Cursor pagination

```text
GET /api/v1/members/cursor?limit=20&status=ACTIVE
GET /api/v1/flats/cursor?limit=20&floorNumber=1
```

```json
{
  "items": [],
  "nextCursor": null,
  "hasNext": false
}
```

When `hasNext` is true, URL-encode `nextCursor` into `after` on the next request. Keep all other filters identical:

```text
GET /api/v1/members/cursor?limit=20&status=ACTIVE&after={encodedNextCursor}
```

Cursor endpoints use ascending `(createdAt, id)` keyset ordering and read at most `limit + 1` rows without a total-count query. Matching indexes are provided. Tokens bind to normalized filters and the current actor/role/object scope; changing them requires restarting pagination. Invalid tokens return `400 INVALID_CURSOR`. Tokens are opaque encodings, not authentication credentials or signed security tokens: record authorization is always enforced independently. Pagination is not a frozen database snapshot, and concurrent changes to filters may move records into/out of the result set. Existing page-number endpoints remain available for direct page navigation.

## Optimistic locking and ETags

JPA `@Version` columns protect members, flats, memberships and application accounts. Member/flat response bodies now contain `version`. Existing service constructors and older stored idempotent member responses remain supported.

GET an individual record:

```text
GET /api/v1/members/{memberId}
GET /api/v1/flats/{flatId}
```

The response includes a strong ETag such as `"member-{uuid}-v2"`. Responses use `Cache-Control: private, no-cache`. Revalidate a read with `If-None-Match`; an unchanged representation returns `304` without a body. Access is checked before returning either 200 or 304.

Send the current ETag in `If-Match` when updating or deactivating a member/flat. A stale tag returns `412 PRECONDITION_FAILED`. Weak tags cannot satisfy If-Match. The service checks the expected version inside the transaction, and JPA's versioned update prevents a race between that check and the commit. Concurrent version conflicts return 412 for conditional writes or 409 for legacy writes.

To preserve existing clients, missing If-Match remains allowed by default. Enable `app.http.require-if-match=true` (environment variable `APP_HTTP_REQUIRE_IF_MATCH=true`) in both backends to require it; missing headers then return `428 PRECONDITION_REQUIRED`. Enable this only after clients start sending ETags. Without If-Match, optimistic locking catches overlapping database transactions but cannot detect a browser form that was stale before its request started. `If-Match: *` asserts existence only; it does not protect against stale form versions.

The Society cache uses a versioned serialization prefix to avoid reusing pre-version flat responses. Membership workflows retain their Redis and database locks in addition to the new version column.

## Contract verification and tests

The Member-owned `contracts/member-to-society-flat-v1.json` fixture is shared by:

- `SocietyConsumerContractTest`: exercises the real Member SocietyClient against the contract's success and missing-resource responses.
- `SocietyProviderContractTest`: exercises Society's actual controller/serialization/error handling against that contract, including ETag revalidation. Business persistence is isolated using a mocked service.

Normal `mvn test` runs these checks. From the repository root on Windows:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-contracts.ps1
```

The script stops if either consumer or provider verification fails. No broker or Pact server is required. This is shared contract verification in this repository, not a separately hosted contract-publishing platform.

PostgreSQL integration tests cover migrations, scoped search, cursor continuation, audit rollback and conflicting concurrent entity updates. Docker must be running for these tests; otherwise Testcontainers marks them skipped. Test-only `docker-java.properties` selects API 1.44 for compatibility with current Docker Desktop and the existing Testcontainers dependency.

## Deployment

Flyway adds Member V5 and Society V4; previously applied migrations are untouched. No migration has been run against your live databases by this code change.

Build new images for all three applications and roll out compatible versions together: the Gateway now refreshes account permissions through Member's new internal endpoint. Kubernetes Gateway routing includes `/api/v1/accounts`. Redis session format changed, so sign in again after deployment. Keep the existing session validation and Gateway authentication tokens consistent across services. Do not expose the new internal account endpoint outside the trusted service path.

An old Member/Gateway image cannot supply or interpret the new authorization scope correctly. Prefer a coordinated development restart over mixed application versions. Do not delete PostgreSQL databases to deploy this change.
