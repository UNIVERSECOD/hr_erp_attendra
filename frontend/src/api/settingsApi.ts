import client from './client.ts'
import { ApiResponse } from '../types'

export interface SystemSettings {
  syncIntervalMinutes: number
}

export const settingsApi = {
  getSystemSettings: () =>
    client.get<ApiResponse<SystemSettings>>('/settings/system'),
  updateSystemSettings: (syncIntervalMinutes: number) =>
    client.put<ApiResponse<SystemSettings>>('/settings/system', { syncIntervalMinutes }),
}
