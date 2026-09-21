import client from './client.ts'
import { ApiResponse, TabelFilters, TabelMonthlyData } from '../types'

export const tabelApi = {
  getMonthly: (params: TabelFilters) =>
    client.get<ApiResponse<TabelMonthlyData>>('/tabel', { params }),
  exportExcel: (params: TabelFilters) =>
    client.get('/tabel/export', { params, responseType: 'blob' }),
}
