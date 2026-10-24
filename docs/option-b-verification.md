# Option B verification — 24 October 2026

The Docker Desktop Kubernetes localhost setup was deployed and verified against the existing `society_db` and `member_db` databases. Their existing PostgreSQL login was configured in `emerald-db-credentials`; database passwords were not reset and databases were not replaced.

| Check | Result |
| --- | --- |
| Society Service | 1/1 Ready |
| Member Service | 1/1 Ready |
| API Gateway | 1/1 Ready |
| Session/lock Redis | 1/1 Ready |
| Society cache Redis | 1/1 Ready |
| Gateway readiness | UP |
| Portal | HTTP 200 |
| Automated Postman requests | 111 passed |
| Postman assertions | 123 passed, zero failures |
| Repeatability | Three successful complete runs, including after redeployment |
| Whole-collection execution | Automated requests run; opt-in manual reference requests skipped |
| Deployment helper | Build, unique image tags, rollout and final status completed successfully |

The three applications use the verified image tag `option-b-20261024135904`. Docker Desktop's existing kind-provisioned cluster was retained; no separate kind CLI or cluster reset was needed.

- [Latest Postman results](verification/option-b/option-b-verification-summary.json)
- [Kubernetes and portal status](verification/option-b/option-b-kubernetes-status.json)
- [Gateway smoke results](verification/option-b/option-b-gateway-smoke-summary.json)
- [Postman usage](postman-option-b.md)

The automated suite checks administrator, owner and family sessions; dynamic and multiple roles; immediate permission changes; family privacy; ownership transfer; validation and authorization failures; idempotency; pagination/search/cursors; ETags; audit; account disablement; and logout. Its uniquely named verification records and audit history are retained, and its test login accounts are disabled. Pre-existing business records are not modified.

`indexLatest.html#option-b` now documents normal startup without namespace deletion, first-deployment-only secret generation, the shared `emerald` PostgreSQL username with a hidden password prompt, immutable build tags, and automated verification. Public Postman environment JSON keeps credentials blank. A ready-to-import private environment was exported to the user's temporary directory; regenerate it with:

```powershell
node scripts/run-postman-option-b.cjs --export-environment
```

To verify the running installation again without redeploying:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/kubernetes/deploy-option-b.ps1 -VerifyOnly
node scripts/run-postman-option-b.cjs --all
node scripts/capture-option-b-status.cjs
```

The localhost port-forward must remain running. Restart it after a Gateway rollout:

```powershell
kubectl --context docker-desktop port-forward service/api-gateway 8080:8080 -n emerald-housing
```
