import client from './client.ts'
import { Holiday } from '../types'

export const holidayApi = {
  getAll: () => client.get('/holidays'),
  getById: (id: number) => client.get(`/holidays/${id}`),
  create: (data: Partial<Holiday>) => client.post('/holidays', data),
  update: (id: number, data: Partial<Holiday>) => client.put(`/holidays/${id}`, data),
  delete: (id: number) => client.delete(`/holidays/${id}`),
}

