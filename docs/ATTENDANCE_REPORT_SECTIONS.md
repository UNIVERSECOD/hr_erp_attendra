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
