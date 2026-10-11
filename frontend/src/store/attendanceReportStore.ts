import { create } from 'zustand'
import { attendanceApi } from '../api/attendanceApi.ts'
import { AttendanceOverviewFilters, AttendanceOverviewRow } from '../types'
import { monthStartInAppTimeZone, todayInAppTimeZone } from '../utils/dateTime.ts'
import { toast } from './toastStore.ts'

interface AttendanceReportState {
  rows: AttendanceOverviewRow[]
  filters: AttendanceOverviewFilters
  page: number
  size: number
  totalElements: number
  totalPages: number
  loading: boolean
  error: string | null
  setFilters: (filters: AttendanceOverviewFilters) => void
  setPage: (page: number) => void
  fetchReport: () => Promise<void>
}

export function reportDateError(filters: AttendanceOverviewFilters) {
  if (!filters.start || !filters.end) return 'Hər iki tarixi seçin.'
  if (filters.start > filters.end) return 'Bitmə tarixi başlanğıcdan əvvəl ola bilməz.'
  if ((Date.parse(filters.end) - Date.parse(filters.start)) / 86400000 >= 366) return 'Ən çox 366 günlük aralıq seçin.'
  return null
}

let requestId = 0
export const useAttendanceReportStore = create<AttendanceReportState>((set, get) => ({
  rows: [],
  filters: { view: 'DETAILS', start: monthStartInAppTimeZone(), end: todayInAppTimeZone() },
  page: 0, size: 20, totalElements: 0, totalPages: 0, loading: false, error: null,
  setFilters: (filters) => {
    requestId++
    set({ filters, page: 0, rows: [], totalElements: 0, totalPages: 0, error: null, loading: !reportDateError(filters) })
  },
  setPage: (page) => { requestId++; set({ page, rows: [], loading: true }) },
  fetchReport: async () => {
    const current = ++requestId
    const { filters, page, size } = get()
    if (reportDateError(filters)) { set({ loading: false, rows: [] }); return }
    set({ loading: true, error: null })
    try {
      const response = await attendanceApi.getOverview({ ...filters, page, size })
      if (current !== requestId) return
      const data = response.data.data
      set({ rows: data?.content ?? [], totalElements: data?.totalElements ?? 0,
        totalPages: data?.totalPages ?? 0, loading: false })
    } catch {
      if (current !== requestId) return
      set({ rows: [], totalElements: 0, totalPages: 0, error: 'Hesabat yüklənmədi.', loading: false })
      toast.error('Hesabat yüklənmədi', 'Yenilə düyməsi ilə təkrar yoxlayın.')
    }
  },
}))
