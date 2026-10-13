import type { AttendanceOverviewFilters, AttendanceReportView } from '../types'
import { daysAgoInAppTimeZone, monthStartInAppTimeZone, todayInAppTimeZone } from './dateTime.ts'

export function defaultAttendanceReportFilters(now: Date = new Date()): AttendanceOverviewFilters {
  const today = todayInAppTimeZone(now)
  return { view: 'DAILY', start: today, end: today }
}

export function attendanceReportDatePresets(now: Date = new Date()) {
  const today = todayInAppTimeZone(now)
  const yesterday = daysAgoInAppTimeZone(1, now)
  return [
    { label: 'Bu gün', start: today, end: today },
    { label: 'Dünən', start: yesterday, end: yesterday },
    { label: 'Bu ay', start: monthStartInAppTimeZone(now), end: today },
    { label: 'Bu il', start: `${today.slice(0, 4)}-01-01`, end: today },
  ]
}

export const attendanceReportMonths = [
  'Yanvar', 'Fevral', 'Mart', 'Aprel', 'May', 'İyun',
  'İyul', 'Avqust', 'Sentyabr', 'Oktyabr', 'Noyabr', 'Dekabr',
]

export function attendanceReportPeriodRange(
  view: 'MONTHLY' | 'YEARLY', year: string, month: string,
): Pick<AttendanceOverviewFilters, 'start' | 'end'> {
  // Keep incomplete year edits out of API/export requests. Calendar arithmetic
  // avoids host time-zone shifts and Date.UTC's special handling of years 0–99.
  if (!/^\d{4}$/.test(year) || Number(year) === 0) return { start: '', end: '' }
  if (view === 'YEARLY') return { start: `${year}-01-01`, end: `${year}-12-31` }
  if (!/^(0[1-9]|1[0-2])$/.test(month)) return { start: '', end: '' }
  const y = Number(year)
  const leap = y % 4 === 0 && (y % 100 !== 0 || y % 400 === 0)
  const lastDay = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][Number(month) - 1]
  return { start: `${year}-${month}-01`, end: `${year}-${month}-${lastDay}` }
}

export function attendanceReportViewRange(
  view: AttendanceReportView,
  dates: Pick<AttendanceOverviewFilters, 'start' | 'end'>,
  now: Date = new Date(),
): Pick<AttendanceOverviewFilters, 'start' | 'end'> {
  if (view === 'DAILY' || view === 'DETAILS') return { start: dates.start, end: dates.end }
  const anchor = /^\d{4}-(0[1-9]|1[0-2])-\d{2}$/.test(dates.start) && !dates.start.startsWith('0000')
    ? dates.start : todayInAppTimeZone(now)
  return attendanceReportPeriodRange(view, anchor.slice(0, 4), anchor.slice(5, 7))
}
