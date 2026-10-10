# Area devices and operation journal — review branch

Implemented on `codex/area-device-audit` at the user's request. Do not merge into `main` or migrate the live installation without acceptance. Tests used separate temporary databases and a bridge simulator.

## Area management

Open **Ərazilər → Cihazları aç**. Each area shows device count, ENTRY/EXIT counts, device name/IP, connection status and last sync independently. Device role changes use the existing local door assignment API. The panel can attach an unassigned device, move it or detach it through `PUT /api/devices/{id}/area`. The existing device edit page uses the same endpoint for area changes. Device configuration/edit links retain the existing controls.

Area changes are local database operations: tenant-validated; physical identities and credentials are preserved; the old door link is cleared on a move; manual employee access survives; automatic area access is reconciled. Physical terminal users are not automatically added/deleted by a local area move. UI explains that terminal reconciliation is separate. Existing physical-device authorization rules still apply to terminal operations.

## Connection notifications

`GET /api/devices/health-overview` matches the tenant's local devices to bridge IDs. The read-only bridge client has a 2-second connection timeout and 5-second response timeout. Missing bridge data/failure returns an unknown state, not a fabricated offline/online transition. Existing long-running sync clients are unchanged.

The authenticated HR application polls once per minute while visible. Active offline devices produce one grouped toast and reminders no more often than every 30 minutes per device. State survives reloads per signed-in account; browser Web Locks coordinate tabs when supported. Disabled/removed devices do not generate recovery notices. Reconnection produces a separate toast; it does not claim attendance catch-up is complete. These are in-app messages, not OS notifications or monitoring while the app is closed. Tests advance a simulated clock; no 30-minute wall-clock wait was needed.

## Historical report areas

Additive migration V039 adds database-owned area snapshot columns and a PostgreSQL trigger to `attendance_logs`. Existing rows receive the best currently known area; earlier moves before this feature cannot be reconstructed. Bridge IDs take precedence over legacy backend IDs, and lookups are tenant-scoped. Rows with no device area fall back to the employee area; unknown areas remain explicitly unknown. Inserts capture the current area. Later edits, device moves/deletions and area renames preserve the snapshot.

Attendance reports and their Excel export read the stored area. This freezes already recorded history, not historical assignment periods for raw device events that have never yet been imported. It does not alter Tabel hours, standard/flexible pairing, midnight allocation or the separately deferred late-event defect. The migration is backward-compatible with the older application; never delete its migration history to roll back a branch.

## Operation journal

**Əməliyyat jurnalı** is available to `HEAD_OFFICE_HR` only. `GET /api/audit-logs` always filters by current tenant and supports exact username, action, entity type, date range and stable pagination. Times are displayed in Asia/Baku.

Hibernate insert/update/delete listeners record successful database changes using the same JDBC connection and transaction. A rollback removes both the change and its journal entry. The authenticated actor is captured before request context cleanup. Background database changes use `Sistem`. Routine health timestamp/online refreshes do not flood the journal.

Coverage: employees, areas, departments, positions, device metadata, doors, area/device memberships, schedule assignments, timetables/day rules, employee permissions, holiday permissions, leave requests, photo metadata, manual attendance corrections, device removal jobs, user metadata/password-change markers and system sync settings. Operational fields show before/after; sensitive contact/identity fields show a change marker only. Passwords/hashes, encrypted credentials, tokens, face bytes/paths and raw request bodies are never copied. Backup settings live in a host file rather than a DB transaction, so their separate summary logging is best-effort and does not make file/DB writes atomic.

This is a change journal, not retroactive history, request/access logging or proof of a physical terminal operation. Rejected operations/read-only requests are not success entries. Direct SQL, migrations and DB cascades bypass Hibernate listeners; do not describe them as captured interactive changes. There is no journal edit/delete API or automatic retention cleanup in this feature.

## Verification

Design follow-up (feature branch only): area device cards now follow the existing Devices page, with matching purple actions, icons, rounded status badges and compact rows. The scrollable dialog keeps its header and assignment footer visible. The journal follows Access Logs styling and uses the existing PaginationBar; the sidebar uses a matching SVG icon. No backend, migration or device protocol behavior changed. Frontend lint/build and the separate review Docker build passed; browser checks covered 390px layout, desktop dialog bounds, move-form cancellation, journal filtering, expanded values and next-page navigation.

- Backend: 313 tests passed; Maven package passed.
- Frontend: lint/build passed; `node --test tests/deviceAlerts.test.cjs` passed four timer/transition tests.
- Dedicated review frontend/backend Docker images built; Compose config validated. Main images/containers were not replaced.
- 23 disposable Docker API checks passed, including transactional rollback, actor/before-after recording, filters/pagination, head-office access, tenant isolation, bridge outage, area move/detach, preservation of manual membership, old report and Excel filters, and new session area.
- V039 also tested against synthetic pre-migration tables: existing rows preserved, bridge/backend ID collision resolved correctly, cross-tenant isolation, frozen old names and current area for new rows.
- Browser verified six-device panel, move success, journal filters and expanded changes, offline/recovery toasts, device edit link and 390px layouts. A sidebar overlap discovered during review was fixed.
- Physical devices and ISAPI firmware were not tested; ISAPI source was unchanged.
