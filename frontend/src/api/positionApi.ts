import client from './client.ts'
import { Position } from '../types'

export const positionApi = {
  getAll: () => client.get<{ data: Position[] }>('/positions'),
  getByDepartment: (departmentId: number) => client.get<{ data: Position[] }>(`/positions/department/${departmentId}`),
  getById: (id: number) => client.get<{ data: Position }>(`/positions/${id}`),
  create: (data: Partial<Position>) => client.post<{ data: Position }>('/positions', data),
  update: (id: number, data: Partial<Position>) => client.put<{ data: Position }>(`/positions/${id}`, data),
  delete: (id: number) => client.delete(`/positions/${id}`),
}
