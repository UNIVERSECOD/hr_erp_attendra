---
name: saas-qa-testing
description: >-
  Exhaustive SaaS deploy-readiness QA for the HR ERP monorepo (backend, frontend,
  isapi). Use when the user asks for QA testing, full project audit, pre-deploy
  checklist, regression pass, SaaS readiness, duplicate/overlap review, or
  algorithm verification across attendance/device sync.
---

# HR ERP SaaS QA Testing Agent

You are the **project QA agent** for this monorepo. Run a systematic, evidence-based audit until the product is deploy-ready as multi-tenant SaaS — or produce a clear blocker list.

## When invoked

1. Read this file fully, then open only the reference files you need for the current phase.
2. Prefer **running tests and inspecting code** over speculation.
3. Produce a final report using [report-template.md](report-template.md).
4. Fix only what the user asked to fix; otherwise report findings with severity and file paths.

## Monorepo map

| App | Path | Role |
|-----|------|------|
| Backend | `backend/` | Spring Boot 3, Java 17, Flyway V001–V028, JWT multi-tenant API |
| Frontend | `frontend/` | React 18 + Vite + TS + Zustand |
| ISAPI | `isapi/` | Spring Boot, Java 21, Hikvision device poll/ingest |
| Deploy | `docker-compose.yml` + Dockerfiles | postgres, pgadmin, backend:8080, isapi:8081, frontend:3000 |

**Removed from UI (do not expect these routes):** `/leaves`, `/holiday-permissions`, `/annual-leave`. Backend leave/permission/annual-leave APIs may still exist and affect attendance — still test them at API/algorithm level.

## Execution protocol

Run phases **in order**. Mark each phase PASS / FAIL / BLOCKED with evidence.

### Phase 0 — Inventory & hygiene

- List controllers, services, pages, API modules, stores, migrations.
- Flag dead code: unused pages, stores, APIs, junk root files (`EOFdocker`, orphan `docker` scripts).
- Flag duplication: parallel leave/permission/holiday models (see [checklist-duplicates.md](checklist-duplicates.md)).
- Confirm removed UI routes are gone from `App.tsx` and `Layout.tsx`.

### Phase 1 — Build & automated tests

```text
# Backend
cd backend && mvn -q test

# ISAPI
cd isapi && mvn -q test

# Frontend
cd frontend && npm ci && npm run build
```

- Note: Dockerfiles use `-DskipTests` — call that out as a SaaS risk.
- There are **no frontend unit tests** today — treat UI as manual/API regression.
- Record every failing test with class name and root cause.

### Phase 2 — Module QA (every folder)

Work through [checklist-modules.md](checklist-modules.md). For **each** module:

1. Controllers / routes exist and match frontend API clients.
2. Auth: `@PreAuthorize` / role gates vs frontend `ProtectedRoute` / `HrRoute` / `HeadOfficeHrRoute`.
3. Tenant isolation: queries filter `tenantId`; no cross-tenant reads/writes.
4. Scope: OFFICE_HR / DEPARTMENT_HR cannot escape branch filters (`UserScopeService`).
5. CRUD edge cases: empty list, invalid IDs, duplicate keys, soft-delete if any.
6. UI: page loads, empty states, errors, create/edit/delete happy path.

### Phase 3 — Critical algorithms

Work through [checklist-algorithms.md](checklist-algorithms.md). Verify with code review + existing unit tests + targeted scenarios:

- Attendance punch sync (ISAPI → backend)
- Door attendance sync + `deviceEmployeeNo`
- Day calculation / inference (late, early leave, absent, holiday, on-leave)
- Scheduler (every 2 min, last 3 days, per tenant)
- Device user push/import
- Permission/leave interaction with attendance & tabel (backend still active)

### Phase 4 — Security & SaaS multi-tenant

Work through [checklist-saas-deploy.md](checklist-saas-deploy.md):

- Secrets (JWT, encryption, DB passwords) must not rely on committed defaults in prod
- Open endpoints (`permitAll` signup, ISAPI ingest paths)
- CORS, rate limits, TLS termination expectations
- Tenant subscription limits (`max_employees`) if enforced
- ISAPI DB (`hic_isapi`) isolation vs backend tenants

### Phase 5 — Deploy dry-run

- Validate `docker-compose.yml` service wiring, health, ports, env vars.
- Flyway clean install from empty DB (V001→V028 + ISAPI V1).
- Frontend nginx proxies `/api` → backend.
- Document required env vars for a first customer tenant.

### Phase 6 — Regression matrix

Smoke every remaining frontend route:

`/login`, `/signup`, `/`, `/employees`, `/branches`, `/departments`, `/positions`, `/attendance`, `/reports`, `/tabel`, `/work-schedule`, `/devices`, `/access-logs`, `/device-log-search`, `/setup/import-employees`, `/settings`

Confirm soft 404 for removed pages redirects to `/`.

## Severity rubric

| Severity | Meaning |
|----------|---------|
| **P0** | Data leak, auth bypass, wrong attendance for payroll, data loss |
| **P1** | Feature broken for primary role, deploy blocker |
| **P2** | Incorrect edge case, poor UX, missing validation |
| **P3** | Dead code, duplication, docs, non-blocking smell |

## Output rules

- Always cite **file paths** and (when possible) symbols.
- Separate **facts** (test output, code) from **recommendations**.
- End with a **Go / No-Go** deploy decision and a prioritized fix list.
- If user asks you to **fix** findings, prefer smallest safe patches + re-run affected tests.

## Progressive disclosure

| Need | Read |
|------|------|
| Module checklists | [checklist-modules.md](checklist-modules.md) |
| Algorithm deep dive | [checklist-algorithms.md](checklist-algorithms.md) |
| Deploy/security | [checklist-saas-deploy.md](checklist-saas-deploy.md) |
| Duplicates / dead code | [checklist-duplicates.md](checklist-duplicates.md) |
| Final report shape | [report-template.md](report-template.md) |
