/**
 * Extracts a human-readable error message from an Axios / API error.
 * Prefers backend `message`, then validation field errors, then a fallback.
 */
export function getApiErrorMessage(error: unknown, fallback = 'Xəta baş verdi'): string {
  const err = error as {
    message?: string
    response?: {
      data?: {
        message?: string
        error?: string
        data?: unknown
        errors?: Record<string, string> | string[]
      }
    }
  }

  const body = err?.response?.data

  const collectFieldErrors = (fieldErrors: unknown): string | null => {
    if (!fieldErrors || typeof fieldErrors !== 'object') return null
    if (Array.isArray(fieldErrors)) {
      const joined = fieldErrors.filter(Boolean).join('; ')
      return joined || null
    }
    const values = Object.values(fieldErrors as Record<string, string>).filter(Boolean)
    return values.length > 0 ? values.join('; ') : null
  }

  const fieldMsg = collectFieldErrors(body?.data) ?? collectFieldErrors(body?.errors)
  if (fieldMsg) return fieldMsg

  if (body?.message?.trim()) {
    return body.message.trim()
  }

  if (body?.error?.trim()) {
    return body.error.trim()
  }

  const axiosMsg = err?.message
  if (axiosMsg && !/^Request failed with status code \d+$/i.test(axiosMsg)) {
    return axiosMsg
  }

  return fallback
}
