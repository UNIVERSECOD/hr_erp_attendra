import client from './client.ts'
import { ApiResponse } from '../types'

export type DataTransferEntity = 'employees' | 'departments' | 'positions'
export type DataTransferFormat = 'xlsx' | 'csv'

export interface DataImportError {
  row: number
  field: string
  message: string
}

export interface DataImportResult {
  entity: DataTransferEntity
  totalRows: number
  importedRows: number
  rejectedRows: number
  deviceSyncDeferred: boolean
  successful: boolean
  errors: DataImportError[]
}

export const dataTransferApi = {
  exportFile: (entity: DataTransferEntity, format: DataTransferFormat) =>
    client.get<Blob>(`/data-transfer/${entity}/export?format=${format}`, { responseType: 'blob' }),

  downloadTemplate: (entity: DataTransferEntity, format: DataTransferFormat) =>
    client.get<Blob>(`/data-transfer/${entity}/template?format=${format}`, { responseType: 'blob' }),

  importFile: (entity: DataTransferEntity, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return client.post<ApiResponse<DataImportResult>>(`/data-transfer/${entity}/import`, formData)
  },
}
