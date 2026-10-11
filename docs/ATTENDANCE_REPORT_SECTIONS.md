# Attendance report sections (review branch)

Implemented on `codex/attendance-report-sections`, based on `d287e60`. The user explicitly requested a separate branch. Do not merge into `main` or replace the main installation until accepted.

## Views and filters

- **Giriş–çıxış detalları** retains the existing separate-visit projection, latest repeated entry, manual correction and frozen area behavior.
- **Gündəlik** shows one employee-day with calculated hours, late/early minutes, permission minutes and status badges. Lateness and early leave are independent: a day can match both filters. A combined filter selects days with both conditions.
- **Aylıq** groups the filtered employee-days by employee/month.
- **İllik** groups the filtered employee-days by employee/year.
- Shared filters: start/end dates, Azerbaijani case-insensitive name, FIN, actual timetable, area, department and position IDs. Area changes clear department/position; department changes clear position. Changing view clears the daily-only status filter.
- Table pagination happens after filtering/aggregation. Excel uses the same complete result set, independent of the current page.
- Requests support up to 366 calendar days inclusive, enough for a leap year; future summary dates and dates before hire are excluded. Without a historical termination date in the schema, terminated staff contribute recorded days only, not fabricated absences.

## Calculation and history

### Dashboard day overview (2026-10-11)

`GET /api/dashboard/attendance-overview` is a tenant- and existing primary-area-scope-aware, read-only endpoint. The compact dashboard panel replaces the former three large cards while retaining the overall employee count. It shows six independent distinct-employee counts: attended during today, absent after scheduled start, late, early departure, approved/active permission for today (hourly included), and approved leave covering today. The shared attendance calculator and request-local schedule resolver provide tolerances, permissions and daily statuses. Inactive/terminated staff and future hires are excluded from these six counts.

Current area occupancy is separate from the day totals. It compares entry/repeated-entry and exit timestamps across recent attendance sessions, counts each employee once in their last recorded area snapshot, handles overnight presence, and lets a later exit supersede older unclosed duplicate rows. It does not infer physical location from current area membership. Entries expired under the existing session policy are excluded from current occupancy and disclosed separately; unknown snapshots have a separate count. A three-day lookback covers all unexpired sessions under the existing policy; this is not a complete historical missing-exit audit. Offline/delayed device data can still make current counts lag reality. This work does not change device ingestion, midnight Tabel allocation, or source attendance statuses.

The panel refreshes every 60 seconds and offers manual refresh. Failed refreshes retain the prior figures with a visible stale-data message instead of replacing them with zeros; initial failure shows dashes. The clock uses Asia/Baku.

Verification: 361 backend tests/package (including six new dashboard scenarios), frontend lint/build, separate Docker builds, 13 isolated Docker API scenarios with synthetic data, and desktop browser rendering/manual refresh. Scenarios include six repeated entries counted once, actual area versus home area, an exit closing current presence, re-entry into another area, all six day metrics, hourly permission and dated leave, expired exits and unauthenticated access. Physical devices were not contacted. Preview remains isolated from main.

### Review follow-up: shared departments and missing exits (2026-10-11)

- Reports and their Excel exports label OPEN/OPEN_SESSION/MISSING_EXIT uniformly as `Çıxış yoxdur`; the one daily missing-exit filter includes both open and missing-exit records. Stored lifecycle statuses and time calculations remain unchanged.
- Department create/edit offers `Bütün ərazilər`. V041 adds `all_areas` default false; the original branch ID remains as a compatibility anchor. This is one shared department, including its positions, not copies per area. Employee validation and employee/position/report/Tabel selectors accept it in any area within the tenant, including areas created later. Omitted flags in legacy updates preserve the setting; omitted schedule fields no longer erase the department's schedule settings.
- Department Excel/CSV adds optional `Bütün ərazilər` (`Bəli`/`Xeyr`, blank means false). The existing `Ərazi` column retains the anchor; employee and position imports resolve shared departments in every area. Duplicate names still require unambiguous import lookup.
- Verification: 355 backend tests/package, frontend lint/build, separate backend/frontend Docker builds, Compose config, 13 isolated API checks and Excel content validation; V041 on populated preview and all 41 migrations on fresh isolated DB. Employee, schedule-assignment and attendance-log hashes remained identical after upgrade and department conversion. Browser edit/save, another-area listing and missing-exit filtering passed. Main installation and physical devices were not changed.

`AttendanceOverviewService` reuses `AttendanceService`'s read-only day inference, shared time/permission calculators and historical schedule resolution. `AttendanceScheduleResolver.forReport` preloads schedule assignments, timetable weekday rules and legacy schedules per request to avoid employee-day SQL queries in annual views. No schema migration is required.

Existing STANDARD first-entry/last-exit daily calculation, flexible closed-interval totals, breaks, tolerances and approved permission handling are unchanged. Midnight allocation and delayed-event reconstruction remain deferred. An overnight interval still belongs to its entry date.

Area filtering uses frozen visited areas when recorded, otherwise current employee area membership for days without punches. A matching day contributes the employee's whole day total; this is not per-area hour allocation. Timetable filters apply to the effective day's schedule, rather than only the employee's current assignment.

The new `/api/attendance/reports` and `/api/attendance/reports/export` endpoints require the existing HR roles and tenant context. Employee selection respects the existing primary-branch user scope. Broader historical role/scope issues remain outside this feature. Old report endpoints stay available. New report reads do not trigger synchronization or write cached summaries; normal background attendance synchronization remains responsible for incoming events.

## Verification

- 347 backend tests passed, including simultaneous late/early filters, aggregation, tenant/branch boundaries, frozen areas, historical schedules, date limits and matching Excel output. Backend package passed.
- Frontend lint and production build passed. Stale requests cannot overwrite newer filters, and invalid dates render field-level feedback.
- Separate backend/frontend Docker images and Compose configuration validated. A disposable PostgreSQL/backend with a mock bridge passed 27 API checks, including approved hourly permissions, overnight totals, named schedule filters, all four Excel exports and unchanged source/cache record counts.
- Browser verified daily combined filters, monthly/yearly totals, dependent filter reset and downloaded filtered annual Excel. The review preview uses synthetic data, its own database and mock bridge. Main runtime and real terminals were not modified.
- ISAPI source was unchanged; ISAPI tests and new physical terminal tests were not run for this feature.
