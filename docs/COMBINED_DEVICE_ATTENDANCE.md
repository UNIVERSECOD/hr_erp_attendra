# Combined entry/exit devices

Implemented on `codex/combined-entry-exit-devices` (2026-10-11). User accepted merging the complete branch into main and rebuilding the main Docker installation on 2026-10-11, together with the status-filter follow-up and renaming visible overtime labels to **Overtime**. This supersedes the original separate-preview-only restriction. See the task ledger for delivery verification.

## Behavior

- Device role `BOTH` is displayed as **Giriş və çıxış** in device creation/editing, the area devices panel and device labels.
- A tenant-owned area is required when assigning the role. Multiple combined devices in an area contribute to one employee/area/calendar-day session. Separate areas have separate sessions.
- Times use `Asia/Baku`. First distinct scan time is entry; the latest later scan time is exit. Every subsequent later scan extends that exit. One scan (or identical timestamps) leaves the exit empty. No automatic 23:59 exit is invented.
- Midnight starts a new bucket, including for NIGHT schedules. This mode is a calendar-day first/last calculation, not an overnight shift-pairing mode.
- Only STANDARD/NIGHT schedule types and their recognized aliases are supported. Flexible and unknown/unassigned schedule types are skipped; original ISAPI events remain available. The schedule is resolved for the event day and frozen on first creation, like existing sessions.
- A delayed earlier event can move the entry earlier; a delayed later event can extend the exit of its own day. Importing a narrower range preserves previously known extrema. Replaying events does not create extra sessions or write unchanged rows.
- Combined sessions are excluded from ordinary ENTRY/EXIT pairing and report-row collapsing. Existing directional behavior and the flexible overnight entry-day allocation remain unchanged.
- Daily summaries and Tabel records are recalculated for each changed employee/day, using the existing schedule/break/permission rules. Combined mode does not change overtime formulas. Raw access events are not modified.
- Manual attendance corrections take precedence over future automatic updates to that combined bucket.

## Configuration boundary and limits

Switching to or from BOTH, or moving a BOTH device to another area (including detaching it), records a new `attendance_role_since`. Only events occurring at or after that boundary are processed under the new configuration. Saving an unchanged role does not reset the boundary. Existing historical rows and area snapshots are preserved. Detaching leaves the role selected but skips processing until an area is assigned again.

This intentionally does not reinterpret old raw events. An event arriving after a configuration change with a timestamp before the boundary remains in the raw journal; reconstructing historical device-role/area assignments is outside this feature. Repeated changes in the same day retain already-created area/day buckets.

The existing scheduled import looks back three days. Older delayed events need an explicit date-range import. Physical firmware/device verification has not been performed for this feature. Tests only use mocks and an isolated ISAPI simulator.

## Storage

Additive Flyway V042 adds `device_configs.attendance_role_since` and `attendance_logs.combined_area_id` / `combined_work_date`, with a unique tenant/employee/area/day index. Existing rows are unchanged. `COMBINED_SESSION` identifies new derived rows; entry/exit punch IDs and bridge device IDs remain traceable. V039 continues to freeze area snapshots.

## Verification

- 393 backend tests pass, including 31 focused device/session tests and 27 report/inference tests.
- Frontend lint and production build pass.
- Separate backend/frontend Docker images build; Compose configuration validates.
- V042 applied to the isolated PostgreSQL preview. 22 API/database checks pass: six devices, first/last updates, delayed earlier scans, 23:59 and Baku midnight, second area, flexible rejection, idempotent replay, manual correction, ordinary exit isolation, daily break/overtime, and raw/pre-existing record preservation.
- Browser creation form shows BOTH and its explanation; device cards show the new role. Screenshot: ignored `runtime/audit/combined-device-role.png`.
- Preview: `http://127.0.0.1:49630/devices`, synthetic data only. Main on port 3000 and physical devices were not changed. ISAPI code is unchanged; its separate suite was not rerun.

Local check artifacts are ignored under `runtime/audit/combined-*`; the fixture script is `build/audit/combined-check.ps1`. It seeds once and must not be rerun against an already-seeded fixture or a real database.
