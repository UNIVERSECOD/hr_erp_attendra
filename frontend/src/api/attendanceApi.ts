import client from './client.ts'
import { ApiResponse, AttendanceReportFilters, EmployeeAttendanceRow, EmployeeAttendanceSummary } from '../types'

export const attendanceApi = {
  getLogs: (employeeId: number, start: string, end: string) =>
    client.get(`/attendance/employee/${employeeId}?start=${start}&end=${end}`),
  getEmployeeAttendance: (employeePk: number, start: string, end: string) =>
    client.get<ApiResponse<EmployeeAttendanceRow[]>>(`/attendance/employee/${employeePk}?start=${start}&end=${end}`),
  getRange: (start: string, end: string) =>
    client.get(`/attendance/range?start=${start}&end=${end}`),
  getAccessLogs: (params?: { deviceId?: number; employeeNo?: string; limit?: number; start?: string; end?: string; page?: number; size?: number }) =>
    client.get('/logs/attendance', { params }),
  getSummary: (employeeId: number, start: string, end: string) =>
    client.get(`/attendance/summary/${employeeId}?start=${start}&end=${end}`),
  getEmployeeAttendanceSummary: (employeePk: number, start: string, end: string) =>
    client.get<ApiResponse<EmployeeAttendanceSummary>>(`/attendance/employee/${employeePk}/summary?start=${start}&end=${end}`),
  log: (data: object) => client.post('/attendance/log', data),

  syncAll: (params: { start: string; end: string; limit?: number }) =>
    client.post('/attendance/sync', null, { params }),
  getReport: (params: AttendanceReportFilters & { page?: number; size?: number }) =>
    client.get('/attendance/report', { params }),
  exportExcel: async (params: AttendanceReportFilters) =>
    client.get('/attendance/report/export', { params, responseType: 'blob' }),
}
