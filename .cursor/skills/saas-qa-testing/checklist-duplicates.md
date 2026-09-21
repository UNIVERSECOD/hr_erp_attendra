# Duplicates, overlaps, and dead code

Flag each as **Keep both (document)** / **Merge** / **Delete**.

## Parallel leave systems

| Concept | API | Model / table | UI status |
|---------|-----|---------------|-----------|
| Permissions / İcazələr | `/api/permissions` | `Leave` via `PermissionService` | Standalone page **removed**; still in WorkSchedule PermissionTab |
| Employee permission grants | `/api/employee-permissions` | `EmployeePermission` | WorkSchedule tab — keep |
| Leave requests | `/api/leaves` | `LeaveRequest` / `LeaveType` | Frontend `leaveApi`/`leaveStore` **removed**; backend may still drive ON_LEAVE |
| Annual leave balances | `/api/annual-leave` | `AnnualLeaveBalance` | Page **removed**; API/service remain |
| Public holidays | `/api/holidays` | `Holiday` | WorkSchedule — keep |
| Bayram icazəsi | `/api/holiday-permissions` | `HolidayPermission` | Page **removed**; API may still affect attendance |

**QA obligation:** Trace attendance + tabel codepaths and list exactly which of the above still flip status/notes. Conflicting sources of truth = P1 product bug.

## Likely dead / stale frontend

After page removal, confirm unused:

- Types only used by deleted pages (trim if safe)
- i18n keys `annualLeave`, `holidayPermissions` (optional cleanup)
- Nav/redirects to `/leaves`

## Backend risks of removed UI

Removing UI does **not** remove schedulers or calc dependencies. Either:

1. Keep backend and document internal management via WorkSchedule / future API, or
2. Soft-disable features that no product owner will maintain.

## Root tree hygiene

Investigate and recommend delete from release:

- Odd non-yml docker leftovers mentioned in overview
- Duplicate docs if contradictory

## Repeated patterns to spot

While scanning, note copy-paste smells:

- Near-identical CRUD controllers without shared base
- Duplicate DTO mapping logic
- Frontend stores that refetch entire lists after every mutation (perf smell P3)
- Same purple/indigo theme tokens hard-coded vs CSS vars (P3)
