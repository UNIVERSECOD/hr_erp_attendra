import { create } from 'zustand'
import { Branch } from '../types'
import { branchApi } from '../api/branchApi.ts'

interface BranchState {
  branches: Branch[]
  loading: boolean
  error: string | null
  fetchBranches: () => Promise<void>
  createBranch: (payload: Partial<Branch>) => Promise<void>
  updateBranch: (id: number, payload: Partial<Branch>) => Promise<void>
  deleteBranch: (id: number) => Promise<void>
}

export const useBranchStore = create<BranchState>((set, get) => ({
  branches: [],
  loading: false,
  error: null,
  fetchBranches: async () => {
    set({ loading: true, error: null })
    try {
      const res = await branchApi.getAll()
      set({ branches: res.data?.data ?? [], loading: false })
    } catch (e: unknown) {
      set({ error: (e as Error).message, loading: false })
    }
  },
  createBranch: async (payload) => {
    await branchApi.create(payload)
    await get().fetchBranches()
  },
  updateBranch: async (id, payload) => {
    await branchApi.update(id, payload)
    await get().fetchBranches()
  },
  deleteBranch: async (id) => {
    await branchApi.remove(id)
    await get().fetchBranches()
  },
}))
