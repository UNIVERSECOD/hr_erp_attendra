import { create } from 'zustand'
import { Timetable, Holiday, Permission, PermissionType } from '../types'
import { timetableApi } from '../api/timetableApi.ts'
import { holidayApi } from '../api/holidayApi.ts'
import { permissionApi } from '../api/permissionApi.ts'

interface ScheduleState {
  timetables: Timetable[]
  holidays: Holiday[]
  permissions: Permission[]
  permissionTypes: PermissionType[]
  loading: boolean
  error: string | null
  fetchTimetables: () => Promise<void>
  createTimetable: (data: Partial<Timetable>) => Promise<void>
  updateTimetable: (id: number, data: Partial<Timetable>) => Promise<void>
  deleteTimetable: (id: number) => Promise<void>
  fetchHolidays: () => Promise<void>
  createHoliday: (data: Partial<Holiday>) => Promise<void>
  updateHoliday: (id: number, data: Partial<Holiday>) => Promise<void>
  deleteHoliday: (id: number) => Promise<void>
  fetchPermissions: () => Promise<void>
  createPermission: (data: Partial<Permission>) => Promise<void>
  updatePermission: (id: number, data: Partial<Permission>) => Promise<void>
  deletePermission: (id: number) => Promise<void>
  fetchPermissionTypes: () => Promise<void>
}

export const useScheduleStore = create<ScheduleState>((set, get) => ({
  timetables: [],
  holidays: [],
  permissions: [],
  permissionTypes: [],
  loading: false,
  error: null,

  fetchTimetables: async () => {
    set({ loading: true, error: null })
    try {
      const res = await timetableApi.getAll()
      set({ timetables: res.data?.data ?? [], loading: false })
    } catch (e: unknown) {
      set({ error: (e as Error).message, loading: false })
    }
  },
  createTimetable: async (data) => {
    await timetableApi.create(data)
    await get().fetchTimetables()
  },
  updateTimetable: async (id, data) => {
    await timetableApi.update(id, data)
    await get().fetchTimetables()
  },
  deleteTimetable: async (id) => {
    await timetableApi.delete(id)
    await get().fetchTimetables()
  },

  fetchHolidays: async () => {
    set({ loading: true, error: null })
    try {
      const res = await holidayApi.getAll()
      set({ holidays: res.data?.data ?? [], loading: false })
    } catch (e: unknown) {
      set({ error: (e as Error).message, loading: false })
    }
  },
  createHoliday: async (data) => {
    await holidayApi.create(data)
    await get().fetchHolidays()
  },
  updateHoliday: async (id, data) => {
    await holidayApi.update(id, data)
    await get().fetchHolidays()
  },
  deleteHoliday: async (id) => {
    await holidayApi.delete(id)
    await get().fetchHolidays()
  },

  fetchPermissions: async () => {
    set({ loading: true, error: null })
    try {
      const res = await permissionApi.getAll()
      set({ permissions: res.data?.data?.content ?? [], loading: false })
    } catch (e: unknown) {
      set({ error: (e as Error).message, loading: false })
    }
  },
  createPermission: async (data) => {
    await permissionApi.create(data)
    await get().fetchPermissions()
  },
  updatePermission: async (id, data) => {
    await permissionApi.update(id, data)
    await get().fetchPermissions()
  },
  deletePermission: async (id) => {
    await permissionApi.remove(id)
    await get().fetchPermissions()
  },

  fetchPermissionTypes: async () => {
    try {
      const res = await permissionApi.getTypes()
      set({ permissionTypes: res.data?.data ?? [] })
    } catch {
      // silently fail - use default types
    }
  },
}))
