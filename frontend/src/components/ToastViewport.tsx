import { useToastStore, type ToastType } from '../store/toastStore.ts'

const styles: Record<ToastType, { accent: string; icon: string; label: string }> = {
  success: { accent: 'border-emerald-500', icon: 'text-emerald-600 bg-emerald-50', label: 'Uğurlu' },
  error: { accent: 'border-red-500', icon: 'text-red-600 bg-red-50', label: 'Xəta' },
  warning: { accent: 'border-amber-500', icon: 'text-amber-700 bg-amber-50', label: 'Xəbərdarlıq' },
  info: { accent: 'border-blue-500', icon: 'text-blue-600 bg-blue-50', label: 'Məlumat' },
}

function ToastIcon({ type }: { type: ToastType }) {
  if (type === 'success') {
    return (
      <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="m5 13 4 4L19 7" />
      </svg>
    )
  }
  if (type === 'error') {
    return (
      <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18 18 6M6 6l12 12" />
      </svg>
    )
  }
  if (type === 'warning') {
    return (
      <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v4m0 4h.01M10.3 3.8 2.4 18a2 2 0 0 0 1.7 3h15.8a2 2 0 0 0 1.7-3L13.7 3.8a2 2 0 0 0-3.4 0Z" />
      </svg>
    )
  }
  return (
    <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9h.01M11 12h1v4h1m8-4a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
    </svg>
  )
}

export default function ToastViewport() {
  const { toasts, remove } = useToastStore()

  return (
    <div
      className="pointer-events-none fixed inset-x-3 top-3 z-[100] flex flex-col items-end gap-2 sm:left-auto sm:right-4 sm:top-4 sm:w-[360px]"
      aria-live="polite"
      aria-atomic="false"
    >
      {toasts.map((toastMessage) => {
        const style = styles[toastMessage.type]
        return (
          <div
            key={toastMessage.id}
            className={`toast-enter pointer-events-auto flex w-full items-start gap-3 border-l-4 ${style.accent} bg-white p-3 shadow-lg ring-1 ring-black/5`}
            role={toastMessage.type === 'error' ? 'alert' : 'status'}
          >
            <div className={`flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full ${style.icon}`}>
              <ToastIcon type={toastMessage.type} />
            </div>
            <div className="min-w-0 flex-1 pt-0.5">
              <p className="break-words text-sm font-semibold text-gray-900">{toastMessage.title}</p>
              {toastMessage.message && (
                <p className="mt-1 break-words text-sm leading-5 text-gray-600">{toastMessage.message}</p>
              )}
              <span className="sr-only">{style.label}</span>
            </div>
            <button
              type="button"
              onClick={() => remove(toastMessage.id)}
              className="flex h-8 w-8 flex-shrink-0 items-center justify-center text-gray-400 transition-colors hover:text-gray-700"
              aria-label="Bildirişi bağla"
              title="Bağla"
            >
              <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18 18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        )
      })}
    </div>
  )
}
