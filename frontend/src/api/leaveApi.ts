import client from './client.ts'
import { LeaveRequest, LeaveType } from '../types'

export const leaveApi = {
  getAll: () => client.get<{ data: LeaveRequest[] }>('/leaves'),
  getTypes: () => client.get<{ data: LeaveType[] }>('/leaves/types'),
  create: (data: Pick<LeaveRequest, 'employeeId' | 'leaveTypeId' | 'startDate' | 'endDate' | 'deductFromLeaveBalance'> & { status: 'APPROVED' | 'PENDING' }) =>
    client.post<{ data: LeaveRequest }>('/leaves', data),
  updateStatus: (id: number, status: 'APPROVED' | 'REJECTED') =>
    client.put<{ data: LeaveRequest }>(`/leaves/${id}/status`, null, { params: { status } }),
  remove: (id: number) => client.delete(`/leaves/${id}`),
}
