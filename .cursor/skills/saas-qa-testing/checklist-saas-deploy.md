# SaaS deploy readiness checklist

## A. Secrets & config

| Item | Risk if wrong | Verify |
|------|---------------|--------|
| `JWT_SECRET` | Token forgery | Strong unique secret per env; no YAML default in prod |
| `ENCRYPTION_SECRET_KEY` | Device password leak | Rotatable; not committed |
| DB passwords | Full compromise | Compose `.env` not in git; strong passwords |
| `CORS_ALLOWED_ORIGINS` | CSRF-ish browser abuse | Exact frontend origins only |
| `ISAPI_BASE_URL` | Sync fails / wrong host | Reachable from backend container |
| Rate limits | Brute force | `RATE_LIMIT_*` tuned for prod |

## B. Authentication / authorization gaps

| Finding to re-check | Severity if open |
|---------------------|------------------|
| `/api/auth/signup` permitAll | P0/P1 — uncontrolled tenant users |
| ISAPI user-info / ingest `permitAll` paths | P0 — forged punches / device ops |
| Method security missing on any controller | P0 |
| Frontend route guards vs API authz mismatch | P1 |

## C. Multi-tenancy

| Check | Pass criteria |
|-------|---------------|
| JWT carries `tenantId` | Always set after login |
| Repositories filter by tenant | Spot-check Employee, Attendance, Device queries |
| Scheduler iterates tenants | No shared mutable context leak across tenants |
| ISAPI DB | Document that device events may need explicit tenant mapping |
| `max_employees` | Enforced on create/import or track as known gap |

## D. Data & migrations

| Check | Pass criteria |
|-------|---------------|
| Flyway backend V001→V028 | Clean DB boots |
| Flyway ISAPI V1 | `hic_isapi` created via init.sql |
| `ddl-auto: validate` | Boot fails on schema drift (good) |
| V028 `device_employee_no` | Present after migrate; used by sync/import |

## E. Docker / runtime

| Check | Pass criteria |
|-------|---------------|
| `docker compose up --build` | All 5 services healthy |
| Frontend → `/api` → backend | Login works via nginx proxy |
| Backend → isapi:8081 | Punches fetchable |
| Memory limits | App does not OOM under light load |
| TZ `Asia/Baku` | Day boundaries match Azerbaijan |
| TLS | Reverse proxy terminates HTTPS (document if external) |
| Images skip tests | Prefer CI that runs tests before push |

## F. Operability

| Check | Pass criteria |
|-------|---------------|
| Health endpoints | Monitoring can scrape |
| Structured logs | Errors include tenantId / deviceId |
| Backup Postgres | Document restore for SaaS |
| Zero leftover junk | Remove root artifact files from release tree |

## G. Frontend production build

```text
cd frontend && npm run build
```

| Check | Pass criteria |
|-------|---------------|
| Build | Zero errors |
| Env | `VITE_API_URL` correct for deploy mode (often empty → relative `/api`) |
| Removed pages | No broken imports |
| Lazy routes | Suspense fallback OK |

## H. Go / No-Go gates

**No-Go if any remain:**

1. Cross-tenant data visible
2. Unauthenticated write to attendance/device identity
3. Docker cannot boot fresh DB
4. Attendance calc systematically wrong for payroll days
5. Default JWT/encryption secrets in production compose

**Go only if:** Phases 1–5 PASS, P0=0, P1 either fixed or explicitly accepted in writing.
