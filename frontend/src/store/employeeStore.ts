import { create } from 'zustand'
import { Employee, PaginatedResponse } from '../types'
import { employeeApi } from '../api/employeeApi.ts'

interface EmployeeState {
  employees: Employee[]
  totalElements: number
  totalPages: number
  currentPage: number
  loading: boolean
  error: string | null
  fetchEmployees: (page?: number, size?: number) => Promise<void>
  createEmployee: (data: Partial<Employee>) => Promise<void>
  updateEmployee: (id: number, data: Partial<Employee>) => Promise<void>
  deleteEmployee: (id: number) => Promise<void>
}

export const useEmployeeStore = create<EmployeeState>((set, get) => ({
  employees: [],
  totalElements: 0,
  totalPages: 0,
  currentPage: 0,
  loading: false,
  error: null,
  fetchEmployees: async (page = 0, size = 20) => {
    set({ loading: true, error: null })
    try {
      const res = await employeeApi.getAll(page, size)
      const data: PaginatedResponse<Employee> = res.data
      set({
        employees: data.content,
        totalElements: data.totalElements,
        totalPages: data.totalPages,
        currentPage: data.currentPage,
        loading: false,
      })
    } catch (e: unknown) {
      set({ error: (e as Error).message, loading: false })
    }
  },
  createEmployee: async (data) => {
    await employeeApi.create(data)
    await get().fetchEmployees(get().currentPage)
  },
  updateEmployee: async (id, data) => {
    await employeeApi.update(id, data)
    await get().fetchEmployees(get().currentPage)
  },
  deleteEmployee: async (id) => {
    await employeeApi.delete(id)
    await get().fetchEmployees(get().currentPage)
  },
}))
