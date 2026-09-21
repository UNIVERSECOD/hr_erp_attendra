# Critical algorithm QA checklist

Review implementation, run related unit tests, and invent edge cases if untested.

## 1. Punch ingest (ISAPI)

**Code:** `isapi/.../AcsIngestService`, `DeviceWorkerService`, `DeviceCursorService`, `IdentityResolveService`

| Case | Expected |
|------|----------|
| Valid ENTRY/EXIT event | Stored; cursor advances |
| Duplicate event | Idempotent / no double attendance |
| Unknown employeeNo | Logged; not mapped to wrong person |
| Device offline / timeout | Retry / cursor not corrupted |
| Clock skew | No negative durations; day boundary Asia/Baku |

## 2. Attendance log sync (backend ← ISAPI)

**Code:** `AttendanceLogSyncService`, `isapi.base-url` `/api/punches`

| Case | Expected |
|------|----------|
| Happy pull window | Logs written for tenant devices |
| Empty punch list | No-op success |
| ISAPI down | Error logged; next scheduler run retries |
| Wrong `ISAPI_BASE_URL` | Clear failure, not silent empty data |

## 3. Door attendance sync

**Code:** `DoorAttendanceSyncService`

| Case | Expected |
|------|----------|
| ACTIVE device with ENTRY/EXIT role | Punches matched via employeeNo / `deviceEmployeeNo` |
| Employee only on branch A device | No wrong-branch attendance |
| Same person imported on multiple branches | Access grant vs employee identity correct (import rules) |
| Odd punch count (1 punch) | Inference chooses PRESENT/ABSENT/LATE safely |
| Dedupe within 60s | Single effective punch |

## 4. Day calculation & inference

**Code:** `AttendanceCalculationService`, `AttendanceInferenceService`

Priority order typically: **ON_LEAVE → HOLIDAY → ABSENT/PRESENT/LATE/EARLY_LEAVE/WORKDAY_COMPLETE**

| Case | Expected |
|------|----------|
| Active leave request | Status ON_LEAVE even if punches exist (confirm product rule) |
| Public holiday | HOLIDAY / non-worked day rules |
| Late beyond grace (`allowedLateMinutes`) | LATE |
| Early leave beyond grace | EARLY_LEAVE |
| Worked minutes vs 480 | Overtime = max(worked − 480, 0) |
| Missing tenant context | Does not default silently to tenant 1 without logging (SaaS bug if it does) |

## 5. Scheduler

**Code:** `AttendanceSyncScheduler` (~every 2 minutes, last 3 days)

| Case | Expected |
|------|----------|
| Multiple tenants | Loop each tenant; no cross-bleed |
| Long run overlaps next tick | No deadlock / duplicate corrupt writes |
| One tenant fails | Others still sync |

## 6. Device user sync outbound

**Code:** `IsapiEmployeeUserSyncService`, `DeviceUserIsapiProxyService`, ISAPI `DeviceUserService` / `IsapiClient`

| Case | Expected |
|------|----------|
| Create employee → push to device | UserInfo Record succeeds |
| Update name / employeeNo | Device updated |
| Delete / deactivate | Device user removed or disabled |
| Auth failure to device | Error surfaced; employee still in DB |

## 7. Device employee import (setup)

**Code:** `HikDeviceUserImportService`, `SetupImportController`, migration `V028__add_device_employee_no.sql`

| Case | Expected |
|------|----------|
| Fresh import | Employees created with `deviceEmployeeNo` |
| Same person other branch | Only access grant, not duplicate identity (product rule) |
| Non-HEAD_OFFICE_HR | 403 |
| Partial device response | Partial success report with failures |

## 8. Leave / permission / holiday effects (backend)

Even with UI pages removed:

| Interaction | Where | Expected |
|-------------|-------|----------|
| `LeaveService.hasActiveLeave` | calc | Matches LeaveRequest windows |
| `EmployeePermission` | tabel / attendance notes | Assignment dates respected |
| `HolidayPermission` | attendance flags | Date range inclusive |
| Annual leave remaining | `AnnualLeaveService.recalculate` | entitlement − used + carryover |

Document conflicting sources of truth (see duplicates checklist).

## 9. Role & scope algorithms

**Code:** `UserScopeService`

| Actor | Must see | Must not see |
|-------|----------|--------------|
| HEAD_OFFICE_HR | All branches in tenant | Other tenants |
| OFFICE_HR | Own branch | Other branches |
| DEPARTMENT_HR | Own dept/branch scope | Out-of-scope employees |
| EMPLOYEE | Minimal / none for HR screens | Admin APIs |

## Verification commands

```text
cd backend && mvn -Dtest=AttendanceCalculationServiceTest,AttendanceLogSyncServiceTest,AttendanceServiceTest,DoorAttendanceSyncServiceTest,HikDeviceUserImportServiceTest,IsapiEmployeeUserSyncServiceTest,LeaveServiceTest,PermissionServiceTest,AnnualLeaveServiceTest,HolidayPermissionServiceTest test
```

If a listed test class is missing (e.g. DoorAttendanceSync), record **coverage gap P1** for SaaS payroll accuracy.
