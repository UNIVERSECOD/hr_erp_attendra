# Attendra task ledger / AI handoff

Reviewed: 2026-10-10. Latest delivered source baseline: `201023b` on `main`; older verification entries below retain their original commit context.

Read `AGENTS.md` and `CODEX_HANDOFF.md` first. Verify Git status/history and current source before editing; this is a handoff, not permission to implement the whole backlog. **Latest user direction (2026-10-10): implement selected tasks 2, 3, 4 and 7 on a separate branch, not main.** This work belongs to `codex/area-device-audit`; do not merge or deploy it over the customer's installation before the user accepts it. Commit author remains `UNIVERSECOD <leylaha@code.edu.az>`.

## 1. Completed in source

- 2026-10-10 selected tasks 2, 3, 4 and 7 implemented on **`codex/area-device-audit` only**. Area device panel shows device direction, connection state and last sync, supports local area assignment/move/detach, preserves manual assignments and reconciles area assignments. Global authenticated HR monitoring groups offline warnings and repeats after 30 minutes while the page is active; recovery and unknown bridge state are separate. V039 freezes attendance area ID/name for existing and new sessions so later device moves/area renames do not rewrite report/Excel area attribution. A tenant-scoped head-office-only operation journal captures database entity changes with actor, time, entity, and safe before/after fields; secrets and private contact/biometric values are omitted or marked changed. Backup settings have a separate safe summary record. See [AREA_DEVICES_AND_AUDIT.md](AREA_DEVICES_AND_AUDIT.md) for coverage and boundaries. Verification: 313 backend tests/package; frontend lint/build and four reminder-policy tests; separate frontend/backend Docker image builds; Compose config; 23 disposable Docker API checks; old-schema snapshot migration checks; browser area move, journal filtering/diff, offline/recovery toasts, edit deep-link and 390px layout. No physical device write or customer DB migration. Delayed attendance pairing, midnight Tabel allocation, broader user/permission fixes and MCG remain deferred.

- 2026-10-09 follow-up tasks 1, 3 and 4 completed on this Windows installation (physical-device checks and MCG excluded by the user). Tabel now exposes the existing position filter; changing area clears department/position, changing department clears position, and table/export share one filter object. Stale requests cannot overwrite newer results. Table/export failures and export success show Azerbaijani toasts. The archive stacks above Tabel on narrow screens so filters stay accessible. Frontend lint/build passed; browser checks against disposable Docker fixtures covered combined filters, dependent resets, empty results, matching downloaded Excel (AUD0002, 16 hours), terminated-name styling, disabled deletion, successful termination, simulated offline-device warning and automatic removal of the pending badge after a simulated completion. Error toasts were verified with a fixture-only 503 response; 390px filter bounds were checked. These were not physical-device tests.
- 2026-10-09 local backup acceptance completed: fixed parameterless script startup on Windows PowerShell 5.1 by resolving ProjectRoot after parameter binding; installer uses current-user Limited tasks unless already elevated and hides the backup console. 70 mock assertions passed on both PowerShell 5.1 and 7. On this host, configured the default C:\\AttendraBackups\\daily destination, created a real backup, restored both databases into a network-isolated disposable PostgreSQL container, and matched aggregate row counts for all 33 backend and 7 ISAPI tables. Configuration-copy hash and reported backup size matched. Both Windows tasks were registered; manual scheduler execution returned 0 and skipped the already completed daily copy; the folder picker task is running and returned the selected path successfully. Triggers were verified for logon + 3 minutes and daily 04:00; a future logon/04:00 execution was not observed. Backup directory access is limited to the current user, SYSTEM and Administrators. No restore was performed over customer databases.
- 2026-10-09 attendance/Tabel audit items 1 and 3 fixed: `/api/attendance/recalculate` now refreshes both attendance records and the daily summaries consumed by Tabel in the same transaction, for each requested day. Single-employee recalculation also preserves the tenant boundary. Existing bulk behavior still skips terminated employees. This is an explicit recalculation fix; editing a timetable alone does not automatically rebuild past summaries. Tabel validates month/year; reports validate pagination and date order, handle large page offsets without integer overflow, and apply date validation to Excel exports too. Missing/malformed query parameters now receive localized HTTP 400 responses. Verification: 304 backend tests and package passed; isolated Docker API/Excel audit passed 41 of 43 checks (the remaining two confirm the separate, unresolved restricted-HR area-scope issue). The fixture confirmed 9 hours consistently in daily attendance, Tabel and Excel after removing a 60-minute break, plus repeatable bulk recalculation without changing raw punches. No schema migration or customer-data recalculation was performed.
- 2026-10-09 employee audit item 3 fixed: oversized multipart uploads return HTTP 413 with an Azerbaijani JSON message instead of generic 500. File limit remains 5 MB; backend/Nginx request limits are 6 MB to allow multipart overhead, and Tomcat drains up to 8 MB of rejected request data to avoid resets near the limit. Nginx also returns localized JSON when it rejects the request itself. `EmployeeUploadLimitIntegrationTest` exercises real embedded Tomcat with mocked photo/device services (exactly 5 MB, one byte over, aggregate limit). Verification: 282 backend tests and package passed; frontend lint/build passed; 17 isolated Docker checks passed through both backend and Nginx, including unchanged stored photos after rejection. Employee branch-scope and cross-tenant photo-access audit findings remain separate, unresolved items.

| Task | Delivered behavior | Commit / source anchors |
| --- | --- | --- |
| Face upload protection | Frontend/backend 5 MB upload limit; device-bound JPEG normalization below 190,000 bytes and max dimension 1024. Local employee/photo save survives device failure; warnings remain visible. A 502 alone does not prove image size was the cause. | `312df80`, `3bac84e`; `EmployeeFaceImageService`, ISAPI `FaceImageNormalizer` |
| Backup section | Configurable host folder path, usage information, Windows backup script and scheduled-task installer. Includes both DBs, face files and protected `.env` copy if present. | `0008dbd`; `BackupSettingsService`, settings UI, `scripts/backup-attendra.ps1` |
| Native folder chooser | Windows helper on loopback port 18765; folder chooser invoked from localhost UI. Helper/task installation is required on each host. | `d737276`, follow-up in `7ec2661`; `scripts/backup-folder-picker.ps1`, `scripts/install-backup-task.ps1` |
| Employee termination | Separate terminated list; active list excludes terminated employees. Old Tabel/profile/photo/history remain. Historical Tabel names marked red with explanatory text; do not add visible employmentStatus/terminationDate columns to the Excel totals sheet. | `7ec2661`; `EmployeeService`, `TabelService`, `EmployeesPage.tsx`, `TabelPage.tsx` |
| Employee actions | Use the existing left actions; replace photo-delete action with termination. Employee-delete controls disabled, backend endpoint retained (delegates to termination). | `7bdfd1b` |
| Offline device cleanup | Persistent per-device jobs created with local termination in one transaction. Attempt after commit; pending work retried by dedicated scheduler, survives restart. UI warning toast and pending-device count; terminated list refreshes every 15 seconds. | `b8a389d`, V038; `EmployeeDeviceRemovalService`, `EmployeeRemovalDeviceClient`, `EmployeeDeviceRemovalScheduler` |

### Backup interpretation and installation

- 2026-10-09 follow-up: UTF-8 JSON reads and a UTF-8 BOM on the host backup script preserve Azerbaijani paths/messages on Windows PowerShell 5.1. Status serialization preserves ISO timestamps/timezones on PowerShell 7. Temporary dump cleanup failure produces a warning on successful backup and never masks the original backup error. Mock regression suite now passes 68 assertions on both shells. Docker is available again; the separate `backup-attendra.Docker.Tests.ps1` verifies real dump/restore of two fixture databases, face/env fixture copies, Unicode paths and daily skip in a disposable network-isolated container. Customer database restore and physical devices are not part of this verification.
- 2026-10-09 review fixes (items 2–4): retention and daily skip now require a structurally complete backup (legacy manifest compatible), unrelated/incomplete/linked folders are preserved, timestamp collisions do not overwrite existing folders, and Windows PowerShell 5.1 retries Docker native-stderr failures for the full readiness window. A new backup must also pass structural validation before retention runs. Regression suite: `scripts/tests/backup-attendra.Tests.ps1`, 49 assertions passed on both Windows PowerShell 5.1 and PowerShell 7. Docker Compose configuration validated; the local Docker daemon was unavailable, so real backup/restore and container execution remain unverified. This change does not address the separate employee branch-scope review finding or the Windows default-encoding test finding.
- Current retention is **183 days of backup copies**, not deletion of application data older than six months and not a six-month-only DB export. Backups contain the full databases. Confirm separately if the user wants different semantics.
- Windows task runs at user logon with a three-minute delay and daily at 04:00; this is not a guarantee of execution immediately at machine power-on before login.
- Script waits up to ten minutes for Docker, normally creates one successful backup per day, and removes expired completed backup folders only after a new successful backup.
- See [BACKUP_SETUP.md](BACKUP_SETUP.md). Native folder selection is a host-helper feature, not something an ordinary browser can open unaided. Verify helper installation and allowed localhost origin if the button fails.
- Do not claim a successful restore merely because a dump was created or its archive listing was readable.

### Termination job semantics and boundaries

- The user accepted a queued cleanup approach instead of rolling back local termination when a terminal is offline.
- Local termination is saved, but device cleanup is not shown as fully successful until all jobs complete. An offline terminal may still allow access until deletion succeeds; confirmation text explicitly warns about this.
- Scheduler checks due jobs with a 60-second fixed delay, up to 25 jobs per tenant per pass. Processing time means this is not a guaranteed 60-second completion SLA. Backend must be running.
- Each job snapshots tenant, terminal employee number, backend device ID, bridge ID, IP and name. The worker checks current ownership/identity and read-only bridge record/status before deletion. Never substitute HR employee code or backend device ID for terminal identities.
- Connection timeout 5 seconds, response timeout 20 seconds per bridge request. Dedicated scheduler avoids blocking attendance scheduling. Pending jobs have no retry limit; changed identities remain pending for administrator investigation.
- Previously terminated employees are **not** automatically enrolled by migration. Existing retry action must be used once if cleanup should be queued for them.
- For design details and recovery behavior, read [EMPLOYEE_TERMINATION_JOBS.md](EMPLOYEE_TERMINATION_JOBS.md). No standalone job-management screen or automatic identity-remapping tool has been delivered.

## 2. Remaining user tasks, proposed order

### Current planning decisions — 2026-10-10

The following records the original planning decisions. The selected area/device, warning, history and journal work was subsequently implemented on the review branch described above. Delayed attendance pairing and midnight allocation remain unimplemented.

1. **Standard attendance and delayed device events:** the user defines standard attendance as the day's first ENTRY-device punch and last EXIT-device punch across the area's devices, with schedule rules applied. Entry and exit terminals are separate. Match by employee and event timestamp, not ingestion time; a newer open entry must not prevent older events from updating the correct work date. Audit found an older day's events can be skipped when a newer open entry exists. Fix and verify replay, out-of-order arrival, multiple gates, missing exits, and preservation of manual corrections.
2. **Area/device UI integration:** reuse the existing `DeviceConfig.branchId` relationship and existing device-name chips in `BranchesPage`. The user confirmed 4–6 devices are at main entrances/exits, not internal rooms. Plan an area detail device list with name, IP, ENTRY/EXIT role, online/offline state, and last successful sync; allow area/device management while preserving employee assignments. Preserve historical report area attribution when moving devices, since current reports resolve areas from current device configuration.
3. **Offline-device warnings:** proposed toast on connection loss and every 30 minutes while the application is open and the issue continues; group multiple offline devices to avoid floods. Show recovery separately from successful attendance catch-up. Online status alone does not prove all punches have been ingested. No monitoring/toast implementation has been delivered.
4. **Deferred explicitly by the user: midnight allocation in flexible attendance/Tabel.** Remember for later; do not change now. Current behavior assigns the entire closed interval to its check-in date: 9 October 20:00 → 10 October 02:00 gives 6 hours on 9 October and no hours from that interval on 10 October. Desired future daily allocation is 4 hours on 9 October plus 2 on 10 October, summing all completed interval portions per calendar day. Frontend session/report display remains one complete interval, 20:00–02:00, 6 hours, with entry/exit dates; do not split that display into two rows or double-count. Flexible attendance is based on actual entry/exit intervals, not fixed timetable bounds; a checkout is required before counting that interval. Cover month boundaries and recalculate every affected date when implementing later.

Planning audit evidence: 38 focused backend tests passed. A disposable PostgreSQL/backend Docker fixture with a local bridge simulator ran 18 checks: 16 passed, two exposed the same delayed-event recovery defect. Four/six-device cross-gate pairing, pagination, standard/flexible totals, duplicate replay, overnight session display/basis, area-filtered Tabel and Excel passed under **current** semantics. The overnight tests credited the whole interval to the entry day; they do not validate the desired future midnight split. No real device was contacted. A later attempt to rerun the exact delayed-exit-only variant was blocked because Docker was stopped; that variant was inspected in source, not successfully rerun. Audit scripts/results are local ignored files under `build/audit/`.

Previously deferred items remain deferred: physical-device acceptance, user/permission issues and audit journal, and the MCG version (last). Previously delivered filtering, backup and employee tasks below remain complete.

### A. Tabel department/position filtering — completed

`TabelPage.tsx` now provides area, department and position choices with dependent resets, a shared table/export filter object and narrow-screen layout. Existing backend filters and historical totals/red terminated names are preserved. User/role scope is a separate deferred item, not a completed part of this filter feature.

### B. User operation log / audit journal — implemented on review branch

The user explicitly selected this task on 2026-10-10, limited to showing who changed what. It is implemented on `codex/area-device-audit`; broader user/permission work remains deferred. The foundation assessment and initial requirements below are historical; use [AREA_DEVICES_AND_AUDIT.md](AREA_DEVICES_AND_AUDIT.md) for current coverage.

User request: show which user performed which operation in the program.

Delivered: head-office-only journal UI/API, tenant-scoped pagination and filters, authenticated actor and safe field changes, transactional database recording, and separate system-worker identity. No historic user actions can be reconstructed. Read-only requests, rejected operations and direct SQL are outside the journal's scope. There is no automatic retention deletion. Broad permission defects documented elsewhere remain unresolved.

### C. Attendra MCG version — explicitly LAST, not implemented

Keep the standard Attendra calculation unchanged. The user requested a separate MCG version, but branch/deployment/feature-flag strategy has not been agreed; do not invent a new product fork without confirmation.

Requested rules:

- A full scheduled eight-hour day, respecting late-entry and early-exit tolerance, displays `1` for the day.
- If arrival is late or departure early beyond tolerance, display worked hours instead.
- Overtime remains expressed in hours.
- Approved permissions and whether permission time is deducted must be considered.

Before implementation, obtain examples/decisions for non-eight-hour schedules, paid/unpaid permission intervals, overnight shifts, breaks, missing punches, holidays/rest days, rounding and monthly totals mixing day markers with hours. Confirm how `1` and overtime appear together. Then create isolated calculation tests and regressions proving standard Attendra is unchanged.

## 3. Outstanding acceptance checks (not new feature authorization)

- Controlled physical-device test: offline termination -> pending job -> reconnect -> deletion confirmed on the intended terminal; mixed online/offline devices; already-absent user; restart with pending work. Mocks are not firmware verification. Obtain explicit authorization and verify bridge ID/IP/name before a real device write.
- Windows backup checks above were completed on this host; repeat installation/acceptance on each customer computer. Never restore over the live database to test backups. Future automatic trigger execution remains distinct from the successful manual scheduler run.
- Browser acceptance above passed on this host using disposable test data and simulated job state; no customer employee was terminated during testing.
- Face normalization still needs controlled acceptance against the actual device models; it cannot guarantee every photo is accepted by the recognition engine.
- Optional future improvement, not yet approved: job-level diagnostic/admin UI and safe resolution of changed device identities. Never silently redirect pending cleanup to a different terminal.

## 4. Last verified runtime and checks

At `b8a389d` on 2026-10-09:

- All **279 backend tests passed** after the final scheduler change; device requests mocked.
- Frontend lint and production build passed; final frontend Docker build passed.
- Backend Docker build and `docker compose config --quiet` passed.
- Local Compose project `hr-erp-back-front`: backend and frontend rebuilt/recreated with `--no-deps`; PostgreSQL/face volumes retained. Frontend HTTP 200 and backend `/api/health` HTTP 200.
- V038 applied successfully; verified through `flyway_schema_history_backend`. Job table was empty at handoff; no actual employee/device deletion was performed during verification.
- A pre-update backend dump was created outside the repository and its archive listing checked. This does not establish a successful restore.
- ISAPI source was unchanged for the job feature; ISAPI tests and physical-device tests were not rerun for that change.
- Local deploy is not a claim that another/customer computer has been updated. Preserve its existing `.env`, DBs, photos and mappings during any update.
- `.agents/` was untracked and deliberately excluded from delivery. Do not stage unrelated files or local secrets.

## 5. Suggested next-AI prompt

> Read AGENTS.md, docs/CODEX_HANDOFF.md and docs/TASK_STATUS.md. Inspect current Git status/history and affected source. Report what is already done and propose the next remaining task for confirmation; do not start MCG yet. Preserve customer data, tenant scope and existing standard attendance behavior. Use toast summaries and field-level validation. Verify device identities and obtain explicit approval before physical writes. Run relevant tests and report checks that could not be performed.
