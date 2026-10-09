# Attendra task ledger / AI handoff

Reviewed: 2026-10-09. Source baseline: `b8a389d` on `main`.

Read `AGENTS.md` and `CODEX_HANDOFF.md` first. Verify Git status/history and current source before editing; this is a handoff, not permission to implement the whole backlog. The user requested grouped tasks, then confirmation and one-at-a-time implementation. Current delivery expectation: verified changes pushed to `main`, author `UNIVERSECOD <leylaha@code.edu.az>`.

## 1. Completed in source

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

### A. Finish Tabel department/position filtering — partial, next candidate

Current evidence: `frontend/src/pages/TabelPage.tsx` already provides department selection. `TabelController` accepts both `departmentId` and `positionId` for monthly data and export; the page does not yet provide a position selector.

Remaining work after confirmation:

1. Trace `TabelPage`, API wrapper, `TabelService`, repositories and tests; reuse existing backend filtering rather than duplicating it.
2. Add Azerbaijani position selection; preserve department, area, month/year and search behavior. Define/reset dependent selections consistently.
3. Send identical filter values for table and Excel export; verify combined filters, empty results and tenant/user scope.
4. Preserve red terminated names and existing totals; no visible status/date columns on the Excel totals sheet.

### B. User operation log / audit journal — foundation only

User request: show which user performed which operation in the program.

Current evidence: `AuditLog` entity, `AuditLogRepository`, `AuditLogService` and a settings label exist. At review, source search found no service callers recording operations and no dedicated audit journal controller/page. Do not present this as a completed logging feature.

Remaining work after confirmation:

1. Agree role visibility, operations covered, retention, and whether failures/read-only actions are logged.
2. Record authenticated actor, tenant, action, entity reference, timestamp (`Asia/Baku` display), outcome and safe description. Cover employee create/edit/termination, device sync/retry, schedules, permissions, backup settings and other agreed mutations.
3. Add tenant-scoped paginated read API and journal UI with user/action/date filters and useful empty/error states.
4. Distinguish the user who requested termination from the background worker that later completed cleanup; do not fabricate an interactive user for scheduled work.
5. Never record passwords, tokens, `.env` contents, biometric image bytes or unnecessary personal data. Test tenant isolation and failed-operation handling.

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
- On the target Windows installation, verify folder chooser, logon/daily backup scheduling, destination permissions, reported usage and an isolated restore drill. Do not restore over the live database to test backups.
- Browser acceptance: left actions, disabled deletion, warning/success toasts, terminated list auto-refresh and historical Tabel/Excel styling.
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
