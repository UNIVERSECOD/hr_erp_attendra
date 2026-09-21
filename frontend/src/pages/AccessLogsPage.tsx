import { useState, useCallback, useMemo, useEffect } from 'react'
import Layout from '../components/Layout.tsx'
import PaginationBar from '../components/PaginationBar.tsx'
import { attendanceApi } from '../api/attendanceApi.ts'
import { useDeviceStore } from '../store/deviceStore.ts'
import { AccessLog } from '../types'
import { doorRoleLabel } from '../i18n/labels.ts'
import { t } from '../i18n/index.ts'
import {
  appDateBoundaryToIso,
  daysAgoInAppTimeZone,
  formatAttendanceDateTime,
  todayInAppTimeZone,
} from '../utils/dateTime.ts'

function eventLabel(doorRole?: string | null) {
  const upper = doorRole?.toUpperCase()
  if (upper === 'ENTRY') return t('accessLogs.entryEvent')
  if (upper === 'EXIT') return t('accessLogs.exitEvent')
  return t('accessLogs.accessEvent')
}

function eventBadgeStyle(doorRole?: string | null): { background: string; color: string } {
  const upper = doorRole?.toUpperCase()
  if (upper === 'ENTRY') return { background: '#d1fae5', color: '#065f46' }
  if (upper === 'EXIT') return { background: '#fee2e2', color: '#991b1b' }
  return { background: '#e0e7ff', color: '#3730a3' }
}

export default function AccessLogsPage() {
  const { devices, fetchDevices } = useDeviceStore()
  const [startDate, setStartDate] = useState(() => daysAgoInAppTimeZone(7))
  const [endDate, setEndDate] = useState(() => todayInAppTimeZone())
  const [deviceIdFilter, setDeviceIdFilter] = useState('')
  const [search, setSearch] = useState('')
  const [logs, setLogs] = useState<AccessLog[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fetched, setFetched] = useState(false)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(24)

  useEffect(() => {
    void fetchDevices()
  }, [fetchDevices])

  const deviceById = useMemo(() => {
    const map = new Map<number, { name?: string; doorRole?: string }>()
    for (const device of devices) {
      const info = { name: device.deviceName, doorRole: device.doorRole }
      map.set(device.id, info)
      const isapiId = Number(device.deviceId)
      if (!Number.isNaN(isapiId)) {
        map.set(isapiId, info)
      }
    }
    return map
  }, [devices])

  const resolveDevice = (log: AccessLog) => {
    const fromApi = {
      name: log.deviceName,
      doorRole: log.doorRole,
    }
    if (log.deviceId == null) return fromApi
    const fromStore = deviceById.get(log.deviceId)
    return {
      name: fromApi.name || fromStore?.name,
      doorRole: fromApi.doorRole || fromStore?.doorRole,
    }
  }

  const fetchLogs = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const selectedDevice = devices.find((d) => String(d.id) === deviceIdFilter.trim())
      const isapiDeviceId = selectedDevice
        ? Number(selectedDevice.deviceId || selectedDevice.id)
        : NaN
      const deviceId = selectedDevice && !Number.isNaN(isapiDeviceId) ? isapiDeviceId : undefined
      // Asia/Baku day boundaries as Instant — never UTC calendar dates via toISOString().
      const start = startDate ? appDateBoundaryToIso(startDate, false) : undefined
      const end = endDate ? appDateBoundaryToIso(endDate, true) : undefined
      // Keep free-text search client-side only; sending it as employeeNo zeroes ISAPI hits.
      const res = await attendanceApi.getAccessLogs({ deviceId, start, end })
      setLogs(res.data?.data ?? [])
      setPage(0)
      setFetched(true)
    } catch (e: unknown) {
      setError((e as Error).message || t('accessLogs.fetchFailed'))
    } finally {
      setLoading(false)
    }
  }, [deviceIdFilter, startDate, endDate, devices])

  // Initial load only — date/device changes use the Search button (avoids request storms).
  useEffect(() => {
    void fetchLogs()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const filteredLogs = useMemo(() => logs.filter((log) => {
    const normalizedSearch = search.trim().toLowerCase()
    const device = resolveDevice(log)
    const matchesSearch = !normalizedSearch || (
      String(log.employeeNo ?? '').toLowerCase().includes(normalizedSearch) ||
      String(log.deviceId ?? '').toLowerCase().includes(normalizedSearch) ||
      String(device.name ?? '').toLowerCase().includes(normalizedSearch) ||
      String(log.firstName ?? '').toLowerCase().includes(normalizedSearch) ||
      String(log.lastName ?? '').toLowerCase().includes(normalizedSearch)
    )
    return matchesSearch
  }), [logs, search, deviceById])

  const sortedLogs = useMemo(() => [...filteredLogs].sort((a, b) => {
    const timeA = a.punchTime ? new Date(a.punchTime).getTime() : 0
    const timeB = b.punchTime ? new Date(b.punchTime).getTime() : 0
    return timeB - timeA
  }), [filteredLogs])

  const totalItems = sortedLogs.length
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize) || 1)

  useEffect(() => {
    if (page > totalPages - 1) {
      setPage(Math.max(0, totalPages - 1))
    }
  }, [page, totalPages])

  useEffect(() => {
    setPage(0)
  }, [search])

  const pageLogs = useMemo(() => {
    const start = page * pageSize
    return sortedLogs.slice(start, start + pageSize)
  }, [sortedLogs, page, pageSize])

  const uniqueEmployees = new Set(filteredLogs.map(l => l.employeeNo).filter(Boolean)).size
  const uniqueDevices = new Set(filteredLogs.map(l => l.deviceId).filter(v => v !== undefined && v !== null)).size

  const handlePageChange = (nextPage: number) => {
    setPage(Math.max(0, Math.min(totalPages - 1, nextPage)))
  }

  const handlePageSizeChange = (newSize: number) => {
    setPageSize(newSize)
    setPage(0)
  }

  return (
    <Layout>
      <div className="p-4 sm:p-8" style={{ background: '#f8fafc', minHeight: '100vh' }}>
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-gray-900">{t('accessLogs.title')}</h1>
          <p className="text-sm text-gray-500 mt-1">{t('accessLogs.subtitle')}</p>
        </div>

        <div className="bg-white rounded-xl shadow-sm p-5 mb-6">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-4 items-end">
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1.5">{t('accessLogs.fromDate')}</label>
              <input
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1.5">{t('accessLogs.toDate')}</label>
              <input
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1.5">{t('accessLogs.device')}</label>
              <select
                value={deviceIdFilter}
                onChange={(e) => setDeviceIdFilter(e.target.value)}
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500 bg-white"
              >
                <option value="">{t('accessLogs.allDevices')}</option>
                {devices.map((device) => (
                  <option key={device.id} value={String(device.id)}>
                    {device.deviceName || device.deviceId}
                    {device.doorRole ? ` — ${doorRoleLabel(device.doorRole)}` : ''}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1.5">{t('common.search')}</label>
              <input
                type="text"
                placeholder={t('accessLogs.employeeNo')}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
              />
            </div>
            <button
              onClick={fetchLogs}
              disabled={loading}
              className="flex items-center justify-center gap-2 px-5 py-2.5 text-sm font-medium text-white rounded-lg disabled:opacity-50 transition-colors"
              style={{ background: '#a855f7' }}
            >
              {loading ? (
                <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
              ) : (
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                </svg>
              )}
              {loading ? t('accessLogs.loading') : t('common.search')}
            </button>
          </div>
        </div>

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg mb-4 text-sm">
            {error}
          </div>
        )}

        {fetched && (
          <>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
              <div className="bg-white rounded-xl p-4 shadow-sm">
                <p className="text-xs text-gray-400">{t('accessLogs.totalEvents')}</p>
                <p className="text-xl font-bold text-gray-900 mt-1">{filteredLogs.length}</p>
              </div>
              <div className="bg-white rounded-xl p-4 shadow-sm">
                <p className="text-xs text-gray-400">{t('accessLogs.uniqueEmployees')}</p>
                <p className="text-xl font-bold mt-1" style={{ color: '#10b981' }}>{uniqueEmployees}</p>
              </div>
              <div className="bg-white rounded-xl p-4 shadow-sm">
                <p className="text-xs text-gray-400">{t('accessLogs.uniqueDevices')}</p>
                <p className="text-xl font-bold mt-1 text-gray-900">{uniqueDevices}</p>
              </div>
            </div>

            {sortedLogs.length === 0 ? (
              <div className="bg-white rounded-xl shadow-sm p-12 text-center text-gray-400">
                {t('accessLogs.noResults')}
              </div>
            ) : (
              <div className="bg-white rounded-xl shadow-sm overflow-hidden">
                <div
                  className="flex flex-wrap items-center justify-between gap-2 px-5 py-3 text-sm"
                  style={{ background: '#f5f3ff', color: '#5b21b6' }}
                >
                  <span className="font-medium">
                    {t('deviceLogSearch.totalEvents', { n: totalItems })}
                  </span>
                  <span className="text-xs sm:text-sm opacity-90">
                    {t('deviceLogSearch.pageOf', { x: page + 1, y: totalPages })}
                    {' · '}
                    {t('deviceLogSearch.showingOnPage', { n: pageLogs.length })}
                  </span>
                </div>

                <div className="space-y-3 p-4 sm:p-5">
                  {pageLogs.map((log) => {
                    const device = resolveDevice(log)
                    const label = eventLabel(device.doorRole)
                    const badge = eventBadgeStyle(device.doorRole)
                    const deviceDisplay = device.name || (log.deviceId != null ? `#${log.deviceId}` : '—')

                    return (
                      <div key={log.id} className="bg-slate-50 rounded-xl p-5 flex items-center gap-5 border border-slate-100">
                        <div
                          className="w-11 h-11 rounded-xl flex items-center justify-center flex-shrink-0"
                          style={{ background: badge.background }}
                        >
                          <svg className="w-5 h-5" style={{ color: badge.color }} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                          </svg>
                        </div>

                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-semibold text-gray-900">
                            {log.firstName || log.lastName
                              ? `${log.firstName ?? ''} ${log.lastName ?? ''}`.trim()
                              : t('accessLogs.employeeLabel', { employeeNo: log.employeeNo || '—' })}
                          </p>
                          <p className="text-xs text-gray-400 mt-0.5">
                            {label} · {deviceDisplay}
                            {device.doorRole ? ` · ${doorRoleLabel(device.doorRole)}` : ''}
                          </p>
                        </div>

                        <div className="hidden md:block text-center min-w-[150px]">
                          <p className="text-xs text-gray-400 mb-0.5">{t('accessLogs.accessTime')}</p>
                          <p className="text-sm font-medium text-gray-700">
                            {formatAttendanceDateTime(log.punchTime)}
                          </p>
                        </div>

                        <div className="hidden lg:block text-center min-w-[140px]">
                          <p className="text-xs text-gray-400 mb-0.5">{t('accessLogs.device')}</p>
                          <p className="text-sm text-gray-700 truncate" title={deviceDisplay}>{deviceDisplay}</p>
                        </div>

                        <span
                          className="px-2.5 py-1 rounded-full text-xs font-medium flex-shrink-0"
                          style={badge}
                        >
                          {label}
                        </span>
                      </div>
                    )
                  })}
                </div>

                <PaginationBar
                  page={page}
                  pageSize={pageSize}
                  totalItems={totalItems}
                  loading={loading}
                  onPageChange={handlePageChange}
                  onPageSizeChange={handlePageSizeChange}
                  idPrefix="access-logs"
                />
              </div>
            )}
          </>
        )}

        {!fetched && !loading && (
          <div className="bg-white rounded-xl shadow-sm p-12 text-center text-gray-400">
            <svg className="w-12 h-12 mx-auto mb-3 text-gray-200" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
            <p>{t('accessLogs.emptyState')}</p>
          </div>
        )}
      </div>
    </Layout>
  )
}
