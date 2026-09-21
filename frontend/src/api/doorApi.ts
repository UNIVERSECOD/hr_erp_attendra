import client from './client.ts'
import { Door } from '../types'

export const doorApi = {
  getByBranch: (branchId: number) =>
    client.get<{ data: Door[] }>(`/doors?branchId=${branchId}`),
  create: (data: { branchId: number; name: string; status?: string }) =>
    client.post<{ data: Door }>('/doors', data),
  update: (id: number, data: { name?: string; status?: string }) =>
    client.put<{ data: Door }>(`/doors/${id}`, data),
  delete: (id: number) =>
    client.delete(`/doors/${id}`),
}
