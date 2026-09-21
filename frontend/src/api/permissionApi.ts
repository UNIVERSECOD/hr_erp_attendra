import client from './client.ts'
import { Permission, PermissionType } from '../types'

interface PermissionFilters {
  search?: string
  status?: string
  type?: string
  start?: string
  end?: string
  page?: number
  size?: number
}

export const permissionApi = {
  getAll: (filters: PermissionFilters = {}) =>
    client.get<{ data: { content: Permission[]; totalElements: number; totalPages: number; currentPage: number; pageSize: number } }>('/permissions', { params: filters }),
  getById: (id: number) => client.get(`/permissions/${id}`),
  create: (data: Partial<Permission>) => client.post('/permissions', data),
  update: (id: number, data: Partial<Permission>) => client.put(`/permissions/${id}`, data),
  remove: (id: number) => client.delete(`/permissions/${id}`),
  approve: (id: number) => client.post(`/permissions/${id}/approve`),
  reject: (id: number) => client.post(`/permissions/${id}/reject`),
  getEmployeeHistory: (employeePk: number, year?: number) =>
    client.get<{ data: Permission[] }>(`/permissions/employee/${employeePk}`, { params: { year } }),
  getTypes: () => client.get<{ data: PermissionType[] }>('/permissions/types'),
  createType: (data: Partial<PermissionType>) => client.post('/permissions/types', data),
  deleteType: (id: number) => client.delete(`/permissions/types/${id}`),
}
