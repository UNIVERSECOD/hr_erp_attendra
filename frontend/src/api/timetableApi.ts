import client from './client.ts'
import { Timetable } from '../types'

export const timetableApi = {
  getAll: () => client.get('/timetables'),
  getById: (id: number) => client.get(`/timetables/${id}`),
  create: (data: Partial<Timetable>) => client.post('/timetables', data),
  update: (id: number, data: Partial<Timetable>) => client.put(`/timetables/${id}`, data),
  delete: (id: number) => client.delete(`/timetables/${id}`),
}
