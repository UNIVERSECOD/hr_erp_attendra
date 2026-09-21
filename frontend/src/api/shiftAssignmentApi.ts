import client from './client.ts'
import { EmployeeShiftAssignment } from '../types'

export const shiftAssignmentApi = {
  getAll: () => client.get<{ data: EmployeeShiftAssignment[] }>('/shift-assignments'),
  getByTimetable: (timetableId: number, date?: string) =>
    client.get<{ data: EmployeeShiftAssignment[] }>(`/shift-assignments/timetable/${timetableId}${date ? `?date=${date}` : ''}`),
  getByEmployee: (employeeId: number) => client.get<{ data: EmployeeShiftAssignment[] }>(`/shift-assignments/employee/${employeeId}`),
  create: (data: Partial<EmployeeShiftAssignment>) =>
    client.post<{ data: EmployeeShiftAssignment }>('/shift-assignments', data),
  update: (id: number, data: Partial<EmployeeShiftAssignment>) =>
    client.put<{ data: EmployeeShiftAssignment }>(`/shift-assignments/${id}`, data),
  remove: (id: number) => client.delete(`/shift-assignments/${id}`),
  bulkAssign: (data: { employeeIds: number[]; timetableId: number; startDate: string; endDate?: string }) =>
    client.post<{ data: EmployeeShiftAssignment[] }>('/shift-assignments/bulk', data),
}
