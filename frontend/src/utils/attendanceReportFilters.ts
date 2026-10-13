import type { AttendanceOverviewFilters } from '../types'
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
