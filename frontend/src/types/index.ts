export interface User {
  id: number
  username: string
  email: string
  firstName?: string
  lastName?: string
  userType: 'HEAD_OFFICE_HR' | 'OFFICE_HR' | 'DEPARTMENT_HR' | 'EMPLOYEE'
  branchId?: number
  departmentId?: number
}

export interface Branch {
  id: number
  name: string
  code?: string
  city?: string
  address?: string
  status?: 'ACTIVE' | 'INACTIVE' | string
  isHeadOffice?: boolean
}

export interface Department {
  id: number
  departmentName: string
  description?: string
  branchId?: number
  parentDepartmentId?: number
  parentDepartmentName?: string
  employeeCount?: number
  createdAt?: string
  calculateOvertime?: boolean
  flexShift?: boolean
  timetable?: string
  timetableId?: number
}

export interface Position {
  id: number
  positionName: string
  description?: string
  departmentId: number
  departmentName?: string
  employeeCount?: number
  createdAt?: string
}

export interface Employee {
  id: number
  employeeId: string
  deviceEmployeeNo?: string
  firstName: string
  lastName: string
  fatherName?: string
  birthDate?: string
  gender?: string
  mobilePhone?: string
  email?: string
  finNumber?: string
  faceId?: string
  cardId?: string
  serialNumber?: string
  contractNumber?: string
  branchId?: number
  branchName?: string
  contractEndDate?: string
  annualLeaveDuration?: number
  annualLeaveBalance?: number
  groupName?: string
  salary?: number
  hourlyRate?: number
  allowance?: string
  emergencyContact?: string
  address?: string
  notes?: string
  faceImageUrl?: string
  departmentId?: number
  departmentName?: string
  positionId?: number
  positionName?: string
  hireDate?: string
  area?: string
  shiftType?: string
  timetableId?: number
  deviceIds?: number[]
  doorAccess?: string[]
  employmentStatus: 'ACTIVE' | 'INACTIVE' | 'ON_LEAVE'
  createdAt?: string
  updatedAt?: string
}

export interface EmployeeSearchResult {
  employeePk: number
  employeeId: string
  firstName: string
  lastName: string
  finNumber?: string
  departmentId?: number
  departmentName?: string
  branchId?: number
  shiftType?: string
}

export interface AttendanceLog {
  id: number
  employeeId: number
  checkInTime?: string
  checkOutTime?: string
  deviceId?: string
  eventType?: string
  status?: string
  createdAt?: string
}

export interface AttendanceSession {
  checkInTime?: string
  checkOutTime?: string
}

export interface EmployeeAttendanceRow {
  date: string
  checkInTime?: string
  checkOutTime?: string
  hoursWorked?: number
  lateMinutes?: number
  earlyLeaveMinutes?: number
  status: 'PRESENT' | 'ABSENT' | 'LATE' | 'EARLY_LEAVE' | 'ON_LEAVE' | 'WORKDAY_COMPLETE' | 'DAY_OFF' | 'OPEN_SESSION' | 'MISSING_EXIT'
  notes?: string
  shiftType?: string
  sessions?: AttendanceSession[]
}

export interface EmployeeAttendanceSummary {
  totalDays: number
  workingDays: number
  totalHours: number
  absentDays: number
  lateDays: number
  leaveDays: number
}

export interface DoorAttendanceSyncResult {
  totalPunches: number
  matchedSessions: number
  createdLogs: number
  skippedEmployees: number
  skippedPunches?: number
  recalculatedDays: number
  unresolvedEmployeeNos?: string[]
}

export interface AccessLog {
  id: number
  deviceId?: number
  employeeNo?: string
  punchTime?: string
  rawEventId?: number
  firstName?: string
  lastName?: string
  deviceName?: string
  doorRole?: string
}

export interface DailyAttendanceSummary {
  id: number
  employeeId: number
  attendanceDate: string
  checkInTime?: string
  checkOutTime?: string
  hoursWorked?: number
  lateMinutes?: number
  earlyLeaveMinutes?: number
  attendanceStatus?: 'PRESENT' | 'ABSENT' | 'LATE' | 'EARLY_LEAVE' | 'ON_LEAVE' | 'WORKDAY_COMPLETE' | 'DAY_OFF' | 'OPEN_SESSION' | 'MISSING_EXIT'
}

export interface LeaveRequest {
  id: number
  employeeId: number
  leaveTypeId: number
  startDate: string
  endDate: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  approvedBy?: number
  approvalDate?: string
  createdAt?: string
}

export interface LeaveType {
  id: number
  leaveCode: string
  leaveName: string
  annualEntitlement?: number
  isPaid?: boolean
}

export interface Timetable {
  id: number
  tenantId?: number
  name: string
  description?: string
  startTime: string
  endTime: string
  crossesMidnight?: boolean
  breakMinutes?: number
  allowedLateMinutes?: number
  allowedEarlyLeaveMinutes?: number
  shiftType?: string
  dayRules?: TimetableDayRule[]
}

export interface TimetableDayRule {
  id?: number
  dayOfWeek: number
  workingDay: boolean
  startTime?: string
  endTime?: string
  breakMinutes: number
  allowedLateMinutes: number
  allowedEarlyLeaveMinutes: number
}

export interface Holiday {
  id: number
  tenantId?: number
  name: string
  description?: string
  holidayDate: string
  applyScope?: string
  targetIds?: number[]
  scopeType?: string
}

export interface HolidayPermission {
  id: number
  name: string
  description?: string
  startDate: string
  endDate: string
  applyScope: 'COMPANY' | 'DEPARTMENT' | 'BRANCH' | 'EMPLOYEE'
  targetIds?: number[]
  employeeIds?: number[]
  status: 'ACTIVE' | 'INACTIVE'
  createdBy?: number
  createdAt?: string
  updatedAt?: string
}

export interface AnnualLeaveBalance {
  id: number
  employeeId: number
  employeeName?: string
  year: number
  entitlementDays: number
  usedDays: number
  remainingDays: number
  carryoverDays: number
  status: 'ACTIVE' | 'INACTIVE'
  createdAt?: string
  updatedAt?: string
}

export interface Permission {
  id: number
  tenantId?: number
  permissionTypeId?: number
  name: string
  description?: string
  leaveType: string
  applyType: string
  targetId?: number
  startDate: string
  endDate: string
  status: string
  reason?: string
  createdBy?: number
  createdAt?: string
  updatedAt?: string
  approvedBy?: number
  approvalDate?: string
  employeeName?: string
  employeeId?: string
  finNumber?: string
}

export interface PermissionType {
  id: number
  tenantId?: number
  code: string
  name: string
  isCustom?: boolean
}

export interface EmployeeShiftAssignment {
  id: number
  tenantId?: number
  employeeId: number
  timetableId: number
  effectiveStartDate: string
  effectiveEndDate?: string
  assignedBy?: number
  assignedAt?: string
  status: 'ACTIVE' | 'INACTIVE'
}

export interface EmployeePermission {
  id: number
  tenantId?: number
  employeeId: number
  permissionTypeId: number
  startDate: string
  endDate: string
  reason?: string
  status: 'ACTIVE' | 'INACTIVE' | 'APPROVED' | 'PENDING' | 'REJECTED'
  approvedBy?: number
  approvalDate?: string
  createdAt?: string
  updatedAt?: string
}

export interface AttendanceReportRow {
  attendanceLogId?: number
  employeePk: number
  employeeId: string
  photoUrl?: string
  fullName: string
  fin?: string
  department?: string
  position?: string
  area?: string
  date: string
  checkInTime?: string
  checkOutTime?: string
  workedMinutes?: number
  verificationMethod?: string
  shiftType?: string
  status?: 'OPEN' | 'MISSING_EXIT' | 'CLOSED' | 'MANUALLY_CORRECTED'
}

export interface OpenAttendanceSession {
  attendanceLogId: number
  employeePk: number
  employeeId: string
  fullName: string
  checkInTime: string
  shiftType?: string
  status: 'OPEN' | 'MISSING_EXIT'
  manualOverride?: boolean
}

export interface AttendanceCorrectionRequest {
  checkInTime: string
  checkOutTime: string
  reason: string
}

export interface AttendanceReportFilters {
  start: string
  end: string
  shiftType?: string
  employeeId?: string
  name?: string
  fin?: string
  position?: string
  department?: string
  area?: string
}

export interface TabelFilters {
  year: number
  month: number
  branchId?: number
  departmentId?: number
  positionId?: number
  search?: string
}

export interface TabelRow {
  employeePk: number
  fin?: string
  fullName: string
  position: string
  daily: Record<string, number | string | null>
  workingDays: number
  totalHours: number
}

export interface TabelMonthlyData {
  year: number
  month: number
  daysInMonth: number
  employees: number
  rows: TabelRow[]
}

export interface Door {
  id: number
  tenantId?: number
  branchId: number
  name: string
  status: string
  createdAt?: string
  updatedAt?: string
}

export interface DeviceConfig {
  id: number
  deviceId: string
  deviceName?: string
  deviceIp: string
  devicePort?: number
  username?: string
  branchId?: number
  doorId?: number
  doorRole?: string
  status?: string
  online?: boolean
  lastSyncTime?: string
}

export interface PaginatedResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  currentPage: number
  pageSize: number
}

export interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
  timestamp?: string
}

export interface LoginResponse {
  token: string
  refreshToken: string
  user: User
}

export interface InitialSetupStatus {
  setupRequired: boolean
  username: string
}
