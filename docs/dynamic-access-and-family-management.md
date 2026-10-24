# Dynamic access, registered owners, and families

This phase extends the existing Member, Society, and Gateway applications. Member owns users, role assignments, permissions, ownership memberships, family records, and their audit events. Society continues to own flats and floors. The deployment remains a single society, `EMERALD`.

## Start and use the portal

Build and restart all three applications together using the repository's existing local or Kubernetes setup. Member automatically applies Flyway migration **V6** to its configured database at startup. Existing migrations and passwords are unchanged. Sign in again after updating the applications.

Open **`/portal.html` on the Gateway origin**, for example `http://localhost:8080/portal.html` when the Gateway uses port 8080. The portal uses the existing cookie session and CSRF flow. No frontend package installation or separate web server is required.

1. Sign in with the existing provisioned administrator.
2. In **Owners & flats**, register an owner, select their flat, and provide initial login credentials. The person, OWNER membership, and FLAT_OWNER account are created atomically.
3. The owner signs in and opens **My family** to add or update family records. A family record does not create a user account.
4. In **Roles**, an administrator creates a role and chooses permissions and their scopes. In **Users**, assign one or more roles to an account.
5. For a family login, an administrator creates/links a person record, links it to the family record, and creates a FAMILY_MEMBER account for that person. The link is an administrative operation; owners cannot attach arbitrary existing identities.

## Seeded roles

| Role | Initial access |
| --- | --- |
| ADMIN, SOCIETY_ADMIN | All initially implemented permissions at society scope. These two recovery roles cannot be edited or disabled. |
| COMMITTEE | Read society information, members, and flats. |
| FLAT_OWNER | Read own person/assigned flats and manage family records under their current OWNER memberships. |
| RESIDENT | Read own person/assigned flats and explicitly linked family record. |
| FAMILY_MEMBER | Read own person, permitted flat information, and explicitly linked family record. |
| TENANT | Read own person/assigned flats and explicitly linked family record. |

Custom roles are data, not a Java enum. An account can have multiple enabled roles; their grants are combined. Disabling a role leaves its assignments in place but removes its effective grants. Re-enabling restores them. Accounts with no enabled role cannot authenticate.

Any role granting SELF or OWN_FLATS access requires an active linked person, including custom roles. This requirement follows the configured scope rather than a fixed list of role names. Ownership start dates are evaluated in UTC consistently with existing membership access.

Role codes and permission codes are stable identifiers. Update descriptions, grants, status, or assignments instead of renaming codes. To retire a role, disable it. To retire a permission, remove it from roles; audit history is retained. Existing single-role account requests remain accepted using `role`; new requests should use `roles`.

## Scopes and business rules

- `SOCIETY`: all applicable records in EMERALD.
- `SELF`: the linked person, or an explicitly linked active family record for FAMILY_VIEW.
- `OWN_FLATS`: permitted flat records; for family operations, only records belonging to the actor's own active OWNER membership. Joint owners manage the family records registered under their respective ownerships.

MEMBER_VIEW accepts SOCIETY or SELF. FLAT_VIEW accepts SOCIETY or OWN_FLATS. FAMILY_VIEW accepts all three scopes; family mutations accept SOCIETY or OWN_FLATS. Other implemented permissions require SOCIETY. One role assigns one scope to each permission; multiple roles can combine scopes.

Backend checks enforce the permissions independently of visible controls. A role name alone grants no network access. Member refreshes enabled roles, grants, linked-person status, and flat assignments during Gateway session validation on subsequent requests. The existing trusted internal tokens are still required.

Only users granted owner-registration and person/account administration permissions can register an owner. The seeded owner role cannot create registered persons, users, roles, or ownership assignments. Adding FLAT_OWNER to an account does not create ownership: a current OWNER membership must exist. Owners may hold multiple flats; multiple owners may be linked to a flat.

Ending ownership immediately removes its current access, including linked family flat access, even if a future end date is recorded. Transfers are immediate and must use a date no later than today. They end the selected previous ownership and create the new ownership in one transaction. Joint ownerships are independent: select each outgoing ownership that must end. Old family records remain attached to historical ownership and visible to authorized society staff. They are not silently transferred to the incoming owner. An outgoing owner retains any access independently granted by other memberships or staff roles.

Family records support names, free-text relationships, optional mobile/email, optional administrator-controlled person links, active status, and versions. Family updates and deactivation require the last-read `version`; stale changes return 409. Inactive records cannot be edited. Account and role edits are versioned too.

Self-permission changes are rejected. Account changes and linked-person deactivation cannot remove the last usable built-in administrator. Built-in administrator-role definitions are protected; create custom roles for configurable administration. Access changes and family/ownership mutations use a shared database transaction lock, plus existing membership locks, to serialize changes. Audit writes commit or roll back with business writes. Passwords are excluded from account audit snapshots.

## API reference

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/accounts/me` | Current account, assigned roles, effective permissions, and permitted flat IDs |
| GET / POST | `/api/v1/accounts` | Page accounts / create a login |
| GET | `/api/v1/accounts/{id}` | Read account with version |
| PUT | `/api/v1/accounts/{id}/permissions` | Set roles, person link, enabled status, and expected version |
| GET / POST | `/api/v1/access/roles` | List / create roles |
| PUT | `/api/v1/access/roles/{code}` | Edit description, enabled status, and grants with expected version |
| GET / POST | `/api/v1/access/permissions` | List / register permission definitions |
| PUT | `/api/v1/access/permissions/{code}` | Update a permission description without changing its stable code |
| GET / POST | `/api/v1/owners` | Page scoped ownerships / register owner, ownership, and account |
| POST | `/api/v1/owners/transfer` | Atomic ownership transfer to an existing active person |
| GET / POST | `/api/v1/families` | Page scoped families / add family record |
| PUT | `/api/v1/families/{id}` | Update details with expected version |
| PATCH | `/api/v1/families/{id}/deactivate` | Deactivate with expected version |
| GET | `/api/v1/members/audit` | Existing audit timeline, now also including roles, permissions, and families |

Accounts, owners, and families support zero-based `page` and `size` (1–100). Family lists accept `flatId`. Existing member/flat search, membership assignment/end APIs, and society audit endpoints remain available.

Example custom role:

```json
{
  "code": "FAMILY_REVIEWER",
  "description": "Review the society family directory",
  "enabled": true,
  "version": 0,
  "grants": [{"permission": "FAMILY_VIEW", "scope": "SOCIETY"}]
}
```

Example account creation:

```json
{
  "username": "reviewer",
  "password": "REPLACE_WITH_PRIVATE_PASSWORD",
  "roles": ["COMMITTEE", "FAMILY_REVIEWER"],
  "societyCode": "EMERALD",
  "memberId": null
}
```

New role definitions and assignments need no code change. Registering a brand-new permission code adds catalogue data; implementing a new business action still requires code that checks that permission. The system does not execute arbitrary permission definitions or provide a general policy language. Built-in administrators receive the initially shipped catalogue; explicitly grant new permissions to a configurable role when adding new actions.

## Validation

Run Maven tests in Member and Society, and use the Member wrapper with `-f ../../infrastructure/api-gateway/pom.xml test` for Gateway. Existing Docker-based tests retain their normal prerequisites.

For the new real-database integration suite without Docker:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/test-access-local.ps1
```

The script uses locally installed PostgreSQL 18 by default (`-PostgresBin` overrides its bin directory), initializes a fresh temporary cluster, chooses a local port, runs tests in a unique schema, and stops that cluster in `finally`. Logs and test data remain in the printed temporary directory for diagnosis. It does not connect to the application's databases. The integration test class is skipped during normal Maven runs unless `access.test.jdbc-url` is supplied.

The portal has DOM component tests for administration screens, owner-only screens, form payloads, CSRF headers, and sign-out. Install their optional test dependency in a temporary directory:

```powershell
npm.cmd install --prefix "$env:TEMP/emerald-portal-dom-tests" --no-audit --no-fund jsdom
node scripts/test-portal.cjs
```

These simulate the DOM and API responses; they do not replace visual browser testing or a full Redis/Gateway/backend deployment test.
