/** Asia/Baku wall-clock helpers for attendance timestamps. */
export const APP_TIME_ZONE = 'Asia/Baku'

/** Today's calendar date in Asia/Baku as yyyy-MM-dd. */
export function todayInAppTimeZone(now: Date = new Date()): string {
  return formatYmdInAppTimeZone(now)
}

/** Calendar date N days before today in Asia/Baku. */
export function daysAgoInAppTimeZone(days: number, now: Date = new Date()): string {
  const shifted = new Date(now.getTime() - days * 24 * 60 * 60 * 1000)
  return formatYmdInAppTimeZone(shifted)
}

/** First day of the current month in Asia/Baku as yyyy-MM-dd. */
export function monthStartInAppTimeZone(now: Date = new Date()): string {
  const parts = ymdPartsInAppTimeZone(now)
  return `${parts.year}-${parts.month}-01`
}

/**
 * Convert a yyyy-MM-dd calendar day in Asia/Baku to an Instant ISO string
 * for backend OffsetDateTime query params (start of day or end of day).
 */
export function appDateBoundaryToIso(dateYmd: string, endOfDay: boolean): string {
  const suffix = endOfDay ? 'T23:59:59.999' : 'T00:00:00.000'
  // Interpret wall clock in Asia/Baku, then emit UTC instant for APIs.
  const asBaku = new Date(`${dateYmd}${suffix}+04:00`)
  return asBaku.toISOString()
}

function formatYmdInAppTimeZone(date: Date): string {
  const parts = ymdPartsInAppTimeZone(date)
  return `${parts.year}-${parts.month}-${parts.day}`
}

function ymdPartsInAppTimeZone(date: Date): { year: string; month: string; day: string } {
  const formatter = new Intl.DateTimeFormat('en-CA', {
    timeZone: APP_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
  const parts = formatter.formatToParts(date)
  const lookup = (type: string) => parts.find((p) => p.type === type)?.value ?? '01'
  return {
    year: lookup('year'),
    month: lookup('month'),
    day: lookup('day'),
  }
}

/**
 * Format an attendance timestamp for display (HH:mm).
 * - Offset/Z values are converted to Asia/Baku.
 * - Bare LocalDateTime strings (no zone) are treated as Asia/Baku wall clock — never as UTC.
 */
export function formatAttendanceTime(value?: string | null): string {
  if (!value) return '—'

  const hasOffset = value.endsWith('Z') || /[+-]\d{2}:\d{2}$/.test(value)
  if (!hasOffset) {
    const match = value.match(/T(\d{2}):(\d{2})/)
    if (match) {
      return `${match[1]}:${match[2]}`
    }
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleTimeString('az-AZ', {
    hour: '2-digit',
    minute: '2-digit',
    timeZone: APP_TIME_ZONE,
  })
}

/** Format date+time for dashboards/reports; same zone rules as {@link formatAttendanceTime}. */
export function formatAttendanceDateTime(value?: string | null): string {
  if (!value) return '—'

  const hasOffset = value.endsWith('Z') || /[+-]\d{2}:\d{2}$/.test(value)
  if (!hasOffset) {
    const match = value.match(/^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2})/)
    if (match) {
      return `${match[1]} ${match[2]}:${match[3]}`
    }
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  const formatter = new Intl.DateTimeFormat('en-CA', {
    timeZone: APP_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
  const parts = formatter.formatToParts(date)
  const lookup = (type: string) => parts.find((p) => p.type === type)?.value ?? ''
  return `${lookup('year')}-${lookup('month')}-${lookup('day')} ${lookup('hour')}:${lookup('minute')}`
}
