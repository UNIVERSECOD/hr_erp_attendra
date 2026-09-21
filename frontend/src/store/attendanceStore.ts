import { create } from 'zustand'
import { DailyAttendanceSummary } from '../types'
import { attendanceApi } from '../api/attendanceApi.ts'

interface AttendanceState {
  summaries: DailyAttendanceSummary[]
  loading: boolean
  error: string | null
  fetchSummary: (employeeId: number, start: string, end: string) => Promise<void>
}

export const useAttendanceStore = create<AttendanceState>((set) => ({
  summaries: [],
  loading: false,
  error: null,
  fetchSummary: async (employeeId, start, end) => {
    set({ loading: true, error: null })
    try {
      const res = await attendanceApi.getSummary(employeeId, start, end)
      set({ summaries: res.data?.data || [], loading: false })
    } catch (e: unknown) {
      set({ error: (e as Error).message, loading: false })
    }
  },
}))
