import { create } from 'zustand'

export type ToastType = 'success' | 'error' | 'warning' | 'info'

export interface ToastMessage {
  id: number
  type: ToastType
  title: string
  message?: string
}

interface ToastOptions {
  title: string
  message?: string
  duration?: number
}

interface ToastState {
  toasts: ToastMessage[]
  add: (type: ToastType, options: ToastOptions) => number
  remove: (id: number) => void
  clear: () => void
}

const DEFAULT_DURATION = 4500
const MAX_VISIBLE_TOASTS = 5
let nextToastId = 1

export const useToastStore = create<ToastState>((set, get) => ({
  toasts: [],
  add: (type, options) => {
    const id = nextToastId++
    const toastMessage: ToastMessage = {
      id,
      type,
      title: options.title,
      message: options.message,
    }

    set((state) => ({
      toasts: [...state.toasts, toastMessage].slice(-MAX_VISIBLE_TOASTS),
    }))

    window.setTimeout(() => get().remove(id), options.duration ?? DEFAULT_DURATION)
    return id
  },
  remove: (id) => set((state) => ({
    toasts: state.toasts.filter((toastMessage) => toastMessage.id !== id),
  })),
  clear: () => set({ toasts: [] }),
}))

function show(type: ToastType, title: string, message?: string, duration?: number) {
  return useToastStore.getState().add(type, { title, message, duration })
}

export const toast = {
  success: (title: string, message?: string, duration?: number) => show('success', title, message, duration),
  error: (title: string, message?: string, duration?: number) => show('error', title, message, duration),
  warning: (title: string, message?: string, duration?: number) => show('warning', title, message, duration),
  info: (title: string, message?: string, duration?: number) => show('info', title, message, duration),
  dismiss: (id: number) => useToastStore.getState().remove(id),
  clear: () => useToastStore.getState().clear(),
}
