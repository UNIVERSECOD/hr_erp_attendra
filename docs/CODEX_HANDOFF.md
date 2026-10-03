# Codex Project Handoff

Last reviewed: 2026-10-03

Repository: `https://github.com/UNIVERSECOD/hr_erp_attendra.git`

Baseline at review: `af5a351 Add user-facing toast notifications`

This document transfers project context to a new computer or Codex session. Read `AGENTS.md` first, then verify this document against the current source and `git log` before changing code.

## 1. System Map

Attendra is a local-network HR and attendance monorepo:

```text
React frontend (:3000)
        |
        v
Spring Boot backend (:8080) ---- PostgreSQL 15
        |
        v
Spring Boot ISAPI bridge (:8081) ---- Hikvision face/access devices
```

- `frontend/`: React 18, TypeScript, Vite, Tailwind, Zustand.
- `backend/`: Java 17, Spring Boot 3, Maven, JPA, Flyway, JWT security.
- `isapi/`: Java 21, Spring Boot, Gradle, Digest-auth device communication.
- `docker-compose.yml`: PostgreSQL, pgAdmin, backend, ISAPI, and frontend.
- Application timezone: `Asia/Baku`.

The browser must call the backend. Device credentials and `ISAPI_API_KEY` must never reach the frontend.

## 2. Important Identities

Several IDs look similar but are not interchangeable:

| Domain | Field | Meaning |
| --- | --- | --- |
| Device | `DeviceConfig.id` | Backend database primary key |
| Device | `DeviceConfig.deviceId` | ISAPI bridge device ID |
| Employee | `Employee.id` | Backend database primary key |
| Employee | `Employee.employeeId` | HR employee code shown in the UI |
| Employee | `Employee.deviceEmployeeNo` | Raw person number used by terminals |
| Security | `tenantId` | Company boundary for tenant-owned data |

Resolve both device IDs and all employee identities before changing sync code.

## 3. Completed Work

The following behavior is implemented in the current `main` history:

- Initial setup requires creation of the first admin password on an empty database.
- JWT session protection, password change, expired-session login redirect, and authentication hardening.
- Configurable attendance synchronization interval.
- Employee create/edit validation, including required FIN handling.
- Camera capture through browser video stream, photo preview, replacement, and removal.
- Employee fields persist on initial create, including department, position, timetable, area, and related assignments.
- Employee profile photos are stored locally and synchronized to assigned devices; partial device failure does not undo employee persistence.
- Area model: branches are presented as `Ərazi`; an employee can belong to multiple areas.
- Devices belong to an area. Employees can be assigned to a device individually or through area membership.
- Per-device employee synchronization can resend assigned employees and available faces.
- Device editing preserves the existing encrypted password when the password field is left blank.
- Unreachable devices are reported offline and successful last-sync history is preserved.
- Device user delete behavior supports the Hikvision `UserInfoDetail/Delete` contract used by the terminals.
- Attendance session lifecycle avoids creating a fake `00:00` entry for an unclosed previous-day session.
- Standard and flexible/overnight shifts are handled by the shared attendance schedule resolver.
- Seven-day schedules support separate hours, breaks, entry tolerance, and early-leave tolerance per day.
- Schedules can be assigned to employees individually or by department; employee edit shows the selected timetable.
- Hourly employee permissions include date, time range, reason, status, and whether permitted time is deducted from worked hours.
- Area filtering is available in reporting.
- Employee, department, and position Excel/CSV template, import, and export flows.
- User-facing toast notifications for CRUD, sync, settings, schedules, permissions, data transfer, and session expiry.

Useful commit trail:

```text
af5a351 Add user-facing toast notifications
2d851b1 Synchronize employee faces across assigned devices
2804816 Protect employee field persistence
0b4379a Harden sessions and authentication
bf2c3df Fix area reporting and bulk permission validation
7d62837 Add CSV and Excel master data transfer
9ed52ff Add area-based device employee synchronization
c8f1e88 Add hourly employee permissions
b19a20f Fix attendance session lifecycle
d472a84 Add weekly schedules and unified attendance rules
831ac77 Allow removing employee preview photos
1d6a62d Preserve device last sync history
48da7c0 Mark unreachable devices offline
00f3e4b Fix device edits preserving credentials
263726c Initial commit
```

## 4. Schedule Migration Behavior

`V033__add_weekly_timetable_rules.sql` introduced seven day-specific rules.

Old schedules did not contain weekday information. To avoid deleting or guessing customer data, migration `V033` copied the old start time, end time, break, lateness tolerance, and early-leave tolerance to all seven days as working days.

Example: an old `08:00-17:00` schedule becomes `08:00-17:00` on Monday through Sunday. The schedule and employee assignments remain intact. An administrator must edit the schedule once and mark Saturday/Sunday or other rest days explicitly.

Do not rewrite `V033` after it has run. Any future correction requires a new migration and a clear business rule because some customers may operate seven days.

## 5. Database And Migration State

Backend Flyway migrations currently run from `V001` through `V037`.

Important recent migrations:

- `V030`: first-admin password setup requirement.
- `V031`: attendance sync interval.
- `V032`: device online state.
- `V033` and `V034`: weekly timetable rules.
- `V035`: attendance session integrity.
- `V036`: hourly employee permissions.
- `V037`: employee areas and device assignment sources.

Rules for future migrations:

1. Add the next migration number; never edit an applied migration.
2. Prefer nullable columns or safe defaults before tightening constraints.
3. Backfill deterministically and preserve existing rows.
4. Add tenant-aware indexes and constraints where appropriate.
5. Test both a fresh database and an upgraded database containing real-shaped data.

## 6. Device And Face Synchronization

The main boundary is:

```text
Frontend -> backend device API -> ISAPI bridge -> terminal
```

Core invariants:

- Backend-to-ISAPI uses `X-API-Key`.
- ISAPI-to-terminal uses Digest authentication.
- Device passwords remain backend/ISAPI secrets.
- Employee save is local-first. Device failure is reported as a partial sync error and does not roll back the employee row.
- A face upload first updates the local profile image, then sends the face to assigned devices.
- Deleting a local profile photo should still succeed when a terminal is offline; terminal cleanup errors must be reported separately.
- Raw access events remain idempotent on `(device_id, serial_no)` and polling cursors move forward monotonically.

Before a real device write, perform a read-only status/search call and verify bridge ID, IP, and device name. Obtain explicit user approval before create, update, delete, face upload/delete, or cursor reset on physical hardware.

## 7. Authentication And Fresh Computers

- A fresh database with no users opens the initial setup flow.
- Copying only source code to another computer does not copy users or passwords because they are database data.
- Updating an existing installation while keeping its PostgreSQL volume preserves its users and login credentials.
- `.env` is intentionally not tracked. Create it from `.env.example` and transfer real secrets through a secure channel, not Git or Codex chat.
- The old `README.md` default-login note may be stale; current initial-setup behavior is authoritative.

## 8. Safe Backup Before Updating A Customer Computer

Create a timestamped backup directory, then back up both databases from inside PostgreSQL:

```powershell
New-Item -ItemType Directory -Force .\backups | Out-Null
docker exec hic_postgres pg_dump -U hic_user -d hic_backend -Fc -f /tmp/hic_backend.dump
docker cp hic_postgres:/tmp/hic_backend.dump .\backups\hic_backend.dump
docker exec hic_postgres pg_dump -U hic_user -d hic_isapi -Fc -f /tmp/hic_isapi.dump
docker cp hic_postgres:/tmp/hic_isapi.dump .\backups\hic_isapi.dump
docker cp hic_backend:/app/uploads/faces .\backups\faces
Copy-Item .env .\backups\.env.backup
```

Store the backup outside the repository and protect it because it contains personal data and secrets.

Never run:

```powershell
docker compose down -v
```

The `-v` option removes the PostgreSQL and face-image volumes.

## 9. Safe Update On Another Computer

From the existing repository directory:

```powershell
git status
git remote -v
git fetch --prune origin
git pull --ff-only origin main
docker compose config
docker compose up -d --build
docker compose ps
```

Do not use `git reset --hard` when the customer computer has uncommitted changes unless those changes have been inspected and backed up.

After startup, verify:

1. Login with the customer's existing credentials.
2. Existing employees, schedules, schedule assignments, areas, devices, and photos are present.
3. Backend and ISAPI health endpoints respond.
4. Device status checks work before running a sync.
5. One read-only employee/device lookup works.
6. Only with user approval, test one controlled device synchronization.

## 10. Verification Checklist

Frontend:

```powershell
cd frontend
npm.cmd ci
npm.cmd run lint
npm.cmd run build
```

Backend:

```powershell
cd backend
mvn test
mvn package -DskipTests
```

ISAPI:

```powershell
cd isapi
.\gradlew.bat test
```

Docker:

```powershell
docker compose config
docker compose up -d --build
docker compose ps
docker compose logs --tail 100 backend isapi frontend
```

At commit `af5a351`, frontend lint and production build passed. Docker CLI and Maven were unavailable in that Codex execution environment, so Docker and backend tests were not rerun for the frontend-only toast change. Do not convert that limitation into a claim that those checks passed.

## 11. Remaining Or Deferred Work

- Multi-project daily tracking is deferred: an employee working across multiple projects, project transitions, and hours per project are not implemented.
- Physical Hikvision behavior still requires controlled end-to-end verification against each firmware/device model when device-related code changes.
- Continue security hardening without breaking local-network use; validate allowed device addresses and never expose credentials to the browser.
- Review older broad documentation such as `PROJECT_OVERVIEW.md` against source before relying on it; parts of it describe earlier intended behavior rather than the current implementation.

## 12. Starting Prompt For A New Codex

Use this prompt after cloning or pulling the repository:

> Read `AGENTS.md` and `docs/CODEX_HANDOFF.md`, then inspect the current Git status and source. Treat source and migrations as authoritative. Preserve the existing database and device behavior, explain the proposed change before editing, run the relevant tests, and do not write to physical devices without my explicit approval.
