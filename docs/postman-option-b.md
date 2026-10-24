# Kubernetes localhost verification (Option B)

Use `indexLatest.html#option-b`. The Gateway is `http://localhost:8080` through a Kubernetes port-forward. Both backend services and both Redis workloads must also be Ready before full API verification.

The `postman` directory contains exactly these two import files:

- `Emerald-Housing-Option-B.postman_collection.json`
- `Emerald-Housing-Option-B.postman_environment.json`

Documentation lives in `docs/postman-option-b.md`. Generated reports live in `docs/verification/option-b/`; running verification does not add files to `postman`.

The collection contains:

- **Option B - Automated verification**: 111 ordered requests, including CSRF/login, validation failures, flat/member CRUD, conditional requests, idempotency, membership history, custom roles, multiple-role accounts, owner/family privacy, live permission changes, ownership transfer, audit, and account disablement.
- **Manual reference (opt-in)**: original exploratory requests, deliberate outages/load and optional HTTPS examples. These are skipped unless `runManualRequests=true`; their prerequisites are not part of Option B.

Install Newman outside the repository, then run from the repository root:

```powershell
npm.cmd install --prefix "$env:TEMP/emerald-postman-runner" --no-audit --no-fund newman
node scripts/check-postman-option-b.cjs
node scripts/run-postman-option-b.cjs
node scripts/run-postman-option-b.cjs --all
```

The full runner reads the existing Kubernetes provisioning credentials into memory. Alternatively provide `EMERALD_ADMIN_USERNAME` and `EMERALD_ADMIN_PASSWORD` in the runner process environment. `EMERALD_GATEWAY_URL` overrides the localhost base URL. No real credential belongs in the committed environment JSON.

The full run writes a sanitized `docs/verification/option-b/option-b-verification-summary.json` and exits nonzero on any failure. It never exports raw Newman request/response data, session cookies, or passwords. Test records use unique run identifiers; existing records are not modified. Test accounts are disabled at the end, and audit/ownership history is retained. Failed runs can leave their uniquely named test records for diagnosis.

`--all` verifies collection-wide execution as well: the same automated requests run and the manual reference requests are skipped. The runner always forces `runManualRequests=false` and checks that every expected automated request was sent.

`node scripts/check-postman-option-b.cjs --gateway-smoke` runs only readiness, anonymous CSRF/session reset, and anonymous access denial. Its `option-b-gateway-smoke-summary.json` is explicitly a partial check; it does not establish backend or business-endpoint health.

In the Postman UI, import both JSON files, select **Emerald Housing - Option B Localhost**, privately fill the admin credentials, enable cookies, and run the automated folder in order. Tokens, record IDs, ETags, versions, and separate owner/family sessions are captured automatically.

To generate an immediately usable private Postman environment with the provisioned application login:

```powershell
node scripts/run-postman-option-b.cjs --export-environment
```

Import the printed file from the temporary directory and select **Emerald Housing - Option B Localhost (private)**. This export contains the application admin credentials; it stays outside the repository. It does not contain the PostgreSQL password. Public environment JSON deliberately keeps credentials blank.

For deployment without namespace deletion:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/kubernetes/deploy-option-b.ps1 -BuildImages -DatabaseCredentialsFile "$env:TEMP/emerald-db-private.json"
```

The private database file must contain the four existing credential fields `society-username`, `society-password`, `member-username`, and `member-password`. Keep it outside the repository. If `emerald-db-credentials` already exists and is correct, omit this parameter. The helper preserves existing Secrets by default and never resets database passwords or replaces existing databases.
