import client from './client.ts'
import { EmployeePermission } from '../types'

export const employeePermissionApi = {
  getAll: () => client.get<{ data: EmployeePermission[] }>('/employee-permissions'),
  getByPermissionType: (permissionTypeId: number, date?: string) =>
    client.get<{ data: EmployeePermission[] }>(`/employee-permissions/permission-type/${permissionTypeId}${date ? `?date=${date}` : ''}`),
  getByEmployee: (employeeId: number) => client.get<{ data: EmployeePermission[] }>(`/employee-permissions/employee/${employeeId}`),
  create: (data: Partial<EmployeePermission>) =>
    client.post<{ data: EmployeePermission }>('/employee-permissions', data),
  update: (id: number, data: Partial<EmployeePermission>) =>
    client.put<{ data: EmployeePermission }>(`/employee-permissions/${id}`, data),
  remove: (id: number) => client.delete(`/employee-permissions/${id}`),
  bulkGrant: (data: {
    employeeIds: number[]
    permissionTypeId: number
    startDate: string
    endDate: string
    reason?: string
    status?: string
  }) => client.post<{ data: EmployeePermission[] }>('/employee-permissions/bulk', data),
}
