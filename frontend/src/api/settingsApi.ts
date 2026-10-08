import client from './client.ts'
import { ApiResponse } from '../types'

export interface SystemSettings {
  syncIntervalMinutes: number
}

export interface BackupSettings {
  enabled: boolean
  folderPath: string
  retentionDays: number
  lastStatus: 'NEVER' | 'SUCCESS' | 'ERROR' | 'SKIPPED' | string
  lastBackupAt: string | null
  lastMessage: string | null
  lastBackupBytes: number
  totalBackupBytes: number
  backendDatabaseBytes: number
  isapiDatabaseBytes: number
  faceImagesBytes: number
  currentDataBytes: number
}

export const settingsApi = {
  getSystemSettings: () =>
    client.get<ApiResponse<SystemSettings>>('/settings/system'),
  updateSystemSettings: (syncIntervalMinutes: number) =>
    client.put<ApiResponse<SystemSettings>>('/settings/system', { syncIntervalMinutes }),
  getBackupSettings: () =>
    client.get<ApiResponse<BackupSettings>>('/settings/backup'),
  updateBackupSettings: (enabled: boolean, folderPath: string) =>
    client.put<ApiResponse<BackupSettings>>('/settings/backup', { enabled, folderPath }),
}
