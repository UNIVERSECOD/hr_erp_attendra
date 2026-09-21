# Module-by-module QA checklist

For each item: verify API contract, authz, tenant scope, frontend wiring, and error handling.

## Auth & users

| Area | Backend | Frontend | Checks |
|------|---------|----------|--------|
| Login / refresh / verify | `AuthController`, `AuthService`, `JwtUtil` | `LoginPage`, `authStore`, `authApi` | Wrong password, expired JWT, refresh rotation |
| Signup / register | `AuthController` signup | `SignupPage` | Role creation matrix; who may create HEAD/OFFICE/DEPT HR |
| Rate limit | `RateLimitFilter` | — | Login brute-force capped |
| Tenant context | `TenantContext`, JWT claims | stored user | Missing tenant → safe fail |

## Organization

| Area | Backend | Frontend | Checks |
|------|---------|----------|--------|
| Employees | `EmployeeController`, `EmployeeService` | `EmployeesPage`, `employeeStore` | CRUD, FIN unique, face upload, device sync fields (`deviceEmployeeNo`) |
| Branches | `BranchController` | `BranchesPage` | Office HR scoped to own branch |
| Departments | `DepartmentController` | `DepartmentsPage` | Branch linkage |
| Positions | `PositionController` | `PositionsPage` | Soft constraints |

## Attendance & reporting

| Area | Backend | Frontend | Checks |
|------|---------|----------|--------|
| Attendance records | `AttendanceController`, `AttendanceService`, calc/inference | `AttendancePage` | Status labels AZ, filters, recalc |
| Sync | `AttendanceLogSyncService`, `DoorAttendanceSyncService`, `AttendanceSyncScheduler` | — | See algorithms checklist |
| Tabel | `TabelController`, `TabelService` | `TabelPage` | Month grid, leave/holiday notes |
| Reports / export | `ReportController`, `ExportController` | `ReportsPage` | Date ranges, empty export |

## Work schedule

| Area | Backend | Frontend | Checks |
|------|---------|----------|--------|
| Timetables | `TimetableController` | `WorkSchedulePage` tabs | Create schedule with late/early grace |
| Holidays (public) | `HolidayController` | schedule holiday tab | Overlaps, all-day flags |
| Shift assignment | `ShiftAssignmentController` | `ShiftAssignmentPage` tab | Employee+date conflicts |
| Permission assignment | `EmployeePermissionController` | `PermissionAssignmentPage` | Still used; does not depend on removed İcazələr page |
| Permissions (legacy UI removed) | `PermissionController` | schedule PermissionTab / API via `permissionApi` | Confirm WorkSchedule still works after UI page removal |

## Devices & access

| Area | Backend | Frontend / ISAPI | Checks |
|------|---------|------------------|--------|
| Devices | `DeviceController`, `DeviceService` | `DevicesPage` | Credentials encrypted at rest |
| Device users | `DeviceUserController`, proxy services | device modals + ISAPI `DeviceUserService` | CRUD on device, employeeNo mapping |
| Import from device | `SetupImportController`, `HikDeviceUserImportService` | `DeviceEmployeeImportPage` | HEAD_OFFICE_HR only; V028 `device_employee_no` |
| Doors | `DoorController` | devices/doors UI if any | ENTRY/EXIT roles |
| Access logs | `LogsController`, `EventReadController` | `AccessLogsPage` | Pagination, filters |
| Device log search | `DeviceLogSearchController` | `DeviceLogSearchPage` | Remote query timeouts |
| ISAPI ingest | `IsapiAccessControlController` (permitAll paths!) | `isapi` ACS ingest | Auth gap for SaaS |

## Dashboard & settings

| Area | Backend | Frontend | Checks |
|------|---------|----------|--------|
| Dashboard | `DashboardController` | `DashboardPage` | Counts match scoped data |
| Settings | — | `SettingsPage` | Persist only client prefs; no secret leak |
| Health | `HealthController` | — | Compose healthchecks |

## Backend-only leave stack (UI removed)

Still test because attendance/tabel may depend on them:

- `LeaveController` / `LeaveService` — `LeaveRequest` active leave → ON_LEAVE
- `PermissionController` / `PermissionService`
- `HolidayPermissionController` / `HolidayPermissionService`
- `AnnualLeaveController` / `AnnualLeaveService`

Confirm: either wire a future UI, document as internal API, or isolate so orphaned tables cannot corrupt attendance unexpectedly.

## Frontend route sanity

After page removal, assert these files do **not** route-import deleted pages:

- `frontend/src/App.tsx`
- `frontend/src/components/Layout.tsx`
- No nav labels: İcazələr, Bayram icazəsi, Mezuniyyətlər

## Test coverage map (existing)

Run and extend when fixing:

**Backend controller tests:** Auth, Dashboard, Device, DeviceUser, Employee, EventRead, IsapiAccessControl, SetupImport  
**Backend service tests:** AnnualLeave, Attendance*, Auth, Branch, Device*, Employee*, HolidayPermission, Isapi*, Leave, Permission, ShiftAssignment, Tabel  
**ISAPI:** DeviceController, DeviceCursor, AcsEventSearchDto  

**Known gaps:** `DoorAttendanceSyncService` unit tests, Report/Export/Tabel controllers, frontend tests.
