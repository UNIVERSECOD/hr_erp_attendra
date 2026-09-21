import client from './client.ts'
import { Branch } from '../types'

export const branchApi = {
  getAll: () => client.get<{ data: Branch[] }>('/branches'),
  getById: (id: number) => client.get<{ data: Branch }>(`/branches/${id}`),
  create: (payload: Partial<Branch>) => client.post<{ data: Branch }>('/branches', payload),
  update: (id: number, payload: Partial<Branch>) => client.put<{ data: Branch }>(`/branches/${id}`, payload),
  remove: (id: number) => client.delete(`/branches/${id}`),
}
