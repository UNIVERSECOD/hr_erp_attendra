import { useState, useCallback, useEffect, useRef } from 'react'
import Layout from '../components/Layout.tsx'
import PaginationBar from '../components/PaginationBar.tsx'
import { deviceLogApi, DeviceLogEvent, DeviceLogSearchResult } from '../api/deviceLogApi.ts'
import { deviceApi } from '../api/deviceApi.ts'
import { DeviceConfig } from '../types'
import { t } from '../i18n/index.ts'
import { doorRoleLabel } from '../i18n/labels.ts'

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

function toLocalDatetimeString(date: Date): string {
  // format for <input type="datetime-local">
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function toIsapiDateTime(localDatetime: string, offsetStr: string): string {
  // Convert "2026-07-08T14:30" → "2026-07-08T14:30:00+04:00"
  if (!localDatetime) return ''
  return localDatetime.length === 16 ? `${localDatetime}:00${offsetStr}` : `${localDatetime}${offsetStr}`
}

/** Derive the local UTC offset string like "+04:00" */
function getLocalOffsetStr(): string {
  const offset = -new Date().getTimezoneOffset()
  const sign = offset >= 0 ? '+' : '-'
  const abs = Math.abs(offset)
  const hh = String(Math.floor(abs / 60)).padStart(2, '0')
  const mm = String(abs % 60).padStart(2, '0')
  return `${sign}${hh}:${mm}`
}

function eventBadgeStyle(description?: string): { background: string; color: string; dot: string } {
  const text = (description ?? '').toLowerCase()
  if (text.includes('authenticated') || text.includes('verified')) {
    return { background: '#d1fae5', color: '#065f46', dot: '#10b981' }
  }
  if (text.includes('door')) {
    return { background: '#dbeafe', color: '#1e40af', dot: '#3b82f6' }
  }
  if (text.includes('login') || text.includes('logout') || text.includes('powering')) {
    return { background: '#fef3c7', color: '#92400e', dot: '#f59e0b' }
  }
  return { background: '#f1f5f9', color: '#475569', dot: '#94a3b8' }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────────────────────────

function Spinner() {
  return (
    <div
      className="w-5 h-5 rounded-full border-2 border-t-transparent animate-spin"
      style={{ borderColor: '#a855f7', borderTopColor: 'transparent' }}
    />
  )
}

interface PhotoModalProps {
  open: boolean
  src: string | null
  loading: boolean
  error: string | null
  fallbackUrl?: string
  employeeName?: string
  onClose: () => void
}

function PhotoModal({ open, src, loading, error, fallbackUrl, employeeName, onClose }: PhotoModalProps) {
  useEffect(() => {
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [onClose])

  if (!open) return null
  return (
    <div
      id="photo-modal-overlay"
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      style={{ background: 'rgba(0,0,0,0.75)', backdropFilter: 'blur(4px)' }}
      onClick={(e) => { if ((e.target as HTMLElement).id === 'photo-modal-overlay') onClose() }}
    >
      <div
        className="relative rounded-2xl overflow-hidden shadow-2xl max-w-2xl w-full"
        style={{ background: '#1e1b4b' }}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4" style={{ borderBottom: '1px solid rgba(255,255,255,0.1)' }}>
          <div>
            <p className="text-white font-semibold text-sm">{t('deviceLogSearch.eventPhoto')}</p>
            {employeeName && <p className="text-xs mt-0.5" style={{ color: '#a5b4fc' }}>{employeeName}</p>}
          </div>
          <button
            onClick={onClose}
            id="photo-modal-close-btn"
            className="w-8 h-8 rounded-lg flex items-center justify-center transition-all"
            style={{ color: '#a5b4fc' }}
            onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.background = 'rgba(168,85,247,0.3)' }}
            onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.background = 'transparent' }}
            aria-label={t('common.close')}
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
        {/* Image */}
        <div className="flex items-center justify-center p-4" style={{ minHeight: '300px', background: '#12103a' }}>
          {loading ? (
            <div className="flex flex-col items-center gap-3">
              <Spinner />
              <p className="text-sm" style={{ color: '#a5b4fc' }}>{t('deviceLogSearch.loadingPhoto')}</p>
            </div>
          ) : error ? (
            <div className="text-center px-6 space-y-4">
              <p className="text-sm text-red-300">{error}</p>
              {fallbackUrl && (
                <a
                  href={fallbackUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium text-white"
                  style={{ background: '#a855f7' }}
                >
                  {t('deviceLogSearch.openPhotoOnDevice')}
                </a>
              )}
            </div>
          ) : src ? (
            <img
              src={src}
              alt={`${t('deviceLogSearch.eventPhoto')}${employeeName ? ' — ' + employeeName : ''}`}
              className="max-w-full max-h-96 rounded-lg object-contain"
              style={{ boxShadow: '0 0 40px rgba(168,85,247,0.3)' }}
            />
          ) : null}
        </div>
      </div>
    </div>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// Main Page
// ─────────────────────────────────────────────────────────────────────────────

export default function DeviceLogSearchPage() {
  const offsetStr = getLocalOffsetStr()

  // Default date range: today 00:00 – 23:59
  const now = new Date()
  const startOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0)
  const endOfDay   = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59)

  // ── Filter state ─────────────────────────────────────────────────────────
  const [deviceId,    setDeviceId]    = useState<string>('')
  const [employeeId,  setEmployeeId]  = useState('')
  const [name,        setName]        = useState('')
  const [cardNo,      setCardNo]      = useState('')
  const [startTime,   setStartTime]   = useState(toLocalDatetimeString(startOfDay))
  const [endTime,     setEndTime]     = useState(toLocalDatetimeString(endOfDay))
  const [page,        setPage]        = useState(0)
  const [pageSize,    setPageSize]    = useState(24)

  // ── UI state ──────────────────────────────────────────────────────────────
  const [devices,   setDevices]   = useState<DeviceConfig[]>([])
  const [result,    setResult]    = useState<DeviceLogSearchResult | null>(null)
  const [loading,   setLoading]   = useState(false)
  const [error,     setError]     = useState<string | null>(null)
  const [fetched,   setFetched]   = useState(false)

  // ── Photo modal state ─────────────────────────────────────────────────────
  const [photoOpen,         setPhotoOpen]         = useState(false)
  const [photoSrc,          setPhotoSrc]          = useState<string | null>(null)
  const [photoName,         setPhotoName]         = useState<string | undefined>(undefined)
  const [photoLoading,      setPhotoLoading]      = useState(false)
  const [photoModalError,   setPhotoModalError]   = useState<string | null>(null)
  const [photoFallbackUrl,    setPhotoFallbackUrl]    = useState<string | undefined>(undefined)
  const [photoBtnLoading,   setPhotoBtnLoading]   = useState<string | null>(null) // pictureURL being loaded
  const [photoError,        setPhotoError]        = useState<string | null>(null)
  const blobUrlsRef = useRef<string[]>([])

  // ── Load device list on mount ─────────────────────────────────────────────
  useEffect(() => {
    deviceApi.getAll()
      .then((res) => setDevices(res.data?.data ?? []))
      .catch(() => setDevices([]))
  }, [])

  // Revoke blob URLs on unmount to free memory
  useEffect(() => {
    const urls = blobUrlsRef.current
    return () => urls.forEach((u) => URL.revokeObjectURL(u))
  }, [])

  // ── Search ────────────────────────────────────────────────────────────────
  const doSearch = useCallback(async (targetPage: number, targetPageSize: number) => {
    if (!deviceId) {
      setError(t('deviceLogSearch.selectDeviceRequired'))
      return
    }
    setLoading(true)
    setError(null)
    setPhotoError(null)
    try {
      const res = await deviceLogApi.search({
        deviceId: Number(deviceId),
        employeeId: employeeId.trim() || undefined,
        name: name.trim() || undefined,
        cardNo: cardNo.trim() || undefined,
        startTime: startTime ? toIsapiDateTime(startTime, offsetStr) : undefined,
        endTime:   endTime   ? toIsapiDateTime(endTime,   offsetStr) : undefined,
        page: targetPage,
        pageSize: targetPageSize,
      })
      const data = res.data?.data ?? null
      setResult(data)
      setPage(targetPage)
      setFetched(true)
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { message?: string } }; message?: string })
        ?.response?.data?.message
        ?? (e as Error)?.message
        ?? t('deviceLogSearch.searchFailed')
      setError(msg)
      setResult(null)
    } finally {
      setLoading(false)
    }
  }, [deviceId, employeeId, name, cardNo, startTime, endTime, offsetStr])

  const handleSearch = () => {
    setPage(0)
    doSearch(0, pageSize)
  }

  const handlePageChange = (nextPage: number) => {
    doSearch(nextPage, pageSize)
  }

  const handlePageSizeChange = (newSize: number) => {
    setPageSize(newSize)
    setPage(0)
    if (fetched && deviceId) doSearch(0, newSize)
  }

  // ── Photo handling ────────────────────────────────────────────────────────
  const handleViewPhoto = async (event: DeviceLogEvent) => {
    if (!event.pictureURL || !deviceId) return
    setPhotoOpen(true)
    setPhotoSrc(null)
    setPhotoModalError(null)
    setPhotoFallbackUrl(event.pictureURL)
    setPhotoName(event.name || event.employeeId)
    setPhotoBtnLoading(event.pictureURL)
    setPhotoLoading(true)
    setPhotoError(null)
    try {
      const blobUrl = await deviceLogApi.fetchPicture(event.pictureURL, Number(deviceId))
      blobUrlsRef.current.push(blobUrl)
      setPhotoSrc(blobUrl)
    } catch (e: unknown) {
      const msg = (e as Error)?.message ?? t('deviceLogSearch.photoLoadFailed')
      setPhotoModalError(msg)
      setPhotoError(msg)
    } finally {
      setPhotoLoading(false)
      setPhotoBtnLoading(null)
    }
  }

  const handleClosePhoto = () => {
    setPhotoOpen(false)
    setPhotoSrc(null)
    setPhotoName(undefined)
    setPhotoModalError(null)
    setPhotoFallbackUrl(undefined)
  }

  // ── Derived values ────────────────────────────────────────────────────────
  const totalPages = result ? Math.max(1, Math.ceil(result.totalMatches / pageSize)) : 1
  const selectedDevice = devices.find((d) => String(d.id) === deviceId)

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <Layout>
      {/* Photo Modal */}
      <PhotoModal
        open={photoOpen}
        src={photoSrc}
        loading={photoLoading}
        error={photoModalError}
        fallbackUrl={photoFallbackUrl}
        employeeName={photoName}
        onClose={handleClosePhoto}
      />

      <div className="p-4 sm:p-8" style={{ background: '#f8fafc', minHeight: '100vh' }}>

        {/* ── Page Header ── */}
        <div className="mb-6">
          <div className="flex items-center gap-3 mb-1">
            <div
              className="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0"
              style={{ background: 'linear-gradient(135deg, #a855f7, #7c3aed)' }}
            >
              <svg className="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                  d="M15 10l4.553-2.069A1 1 0 0121 8.867V15.1a1 1 0 01-1.447.894L15 14M3 8a2 2 0 012-2h8a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2V8z" />
              </svg>
            </div>
            <div>
              <h1 className="text-2xl font-bold text-gray-900">{t('deviceLogSearch.title')}</h1>
              <p className="text-sm text-gray-500 mt-0.5">
                {t('deviceLogSearch.subtitle')}
              </p>
            </div>
          </div>

          {/* Status badge */}
          {selectedDevice && (
            <div className="flex items-center gap-2 mt-3 ml-13">
              <span
                className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium"
                style={{ background: '#ede9fe', color: '#7c3aed' }}
              >
                <span
                  className="w-1.5 h-1.5 rounded-full"
                  style={{ background: selectedDevice.status === 'ACTIVE' ? '#10b981' : '#f59e0b' }}
                />
                {selectedDevice.deviceName ?? selectedDevice.deviceIp}
                {selectedDevice.doorRole && ` — ${doorRoleLabel(selectedDevice.doorRole)}`}
                &nbsp;·&nbsp;{selectedDevice.deviceIp}
              </span>
            </div>
          )}
        </div>

        {/* ── Filter Panel ── */}
        <div className="bg-white rounded-2xl shadow-sm p-6 mb-6" style={{ border: '1px solid #f1f5f9' }}>
          <p className="text-xs font-semibold text-gray-400 uppercase tracking-widest mb-4">{t('deviceLogSearch.searchFilters')}</p>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">

            {/* Device selector — required */}
            <div className="xl:col-span-2">
              <label className="block text-xs font-medium text-gray-500 mb-1.5" htmlFor="device-select">
                {t('deviceLogSearch.device')}
              </label>
              <div className="relative">
                <select
                  id="device-select"
                  value={deviceId}
                  onChange={(e) => { setDeviceId(e.target.value); setFetched(false); setResult(null) }}
                  className="w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm appearance-none focus:outline-none focus:ring-2"
                  style={{ '--tw-ring-color': '#a855f7', paddingRight: '2.5rem' } as React.CSSProperties}
                >
                  <option value="">{t('deviceLogSearch.selectDevice')}</option>
                  {devices.map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.deviceName ?? d.deviceIp}
                      {d.doorRole ? ` — ${doorRoleLabel(d.doorRole)}` : ''}
                      {d.deviceIp ? ` (${d.deviceIp})` : ''}
                    </option>
                  ))}
                </select>
                <div className="absolute inset-y-0 right-3 flex items-center pointer-events-none">
                  <svg className="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                  </svg>
                </div>
              </div>
            </div>

            {/* Employee ID */}
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1.5" htmlFor="filter-employee-id">
                {t('deviceLogSearch.employeeId')}
              </label>
              <input
                id="filter-employee-id"
                type="text"
                placeholder="məs. EMP0009"
                value={employeeId}
                onChange={(e) => setEmployeeId(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                className="w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:ring-2"
                style={{ '--tw-ring-color': '#a855f7' } as React.CSSProperties}
              />
            </div>

            {/* Name */}
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1.5" htmlFor="filter-name">
                {t('deviceLogSearch.name')}
              </label>
              <input
                id="filter-name"
                type="text"
                placeholder="məs. Said"
                value={name}
                onChange={(e) => setName(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                className="w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:ring-2"
              />
            </div>

            {/* Card No */}
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1.5" htmlFor="filter-card-no">
                {t('deviceLogSearch.cardNo')}
              </label>
              <input
                id="filter-card-no"
                type="text"
                placeholder={t('deviceLogSearch.cardNumber')}
                value={cardNo}
                onChange={(e) => setCardNo(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                className="w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:ring-2"
              />
            </div>

            {/* Event Type — fixed */}
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1.5">{t('deviceLogSearch.eventType')}</label>
              <div
                className="w-full border rounded-xl px-3 py-2.5 text-sm flex items-center gap-2"
                style={{ border: '1px solid #f1f5f9', background: '#fafafa', color: '#6b7280' }}
              >
                <span
                  className="inline-block w-2 h-2 rounded-full flex-shrink-0"
                  style={{ background: '#a855f7' }}
                />
                {t('deviceLogSearch.accessControlEvent')}
              </div>
            </div>

            {/* Start Time */}
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1.5" htmlFor="filter-start-time">
                {t('deviceLogSearch.startTime')}
              </label>
              <input
                id="filter-start-time"
                type="datetime-local"
                value={startTime}
                onChange={(e) => setStartTime(e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:ring-2"
              />
            </div>

            {/* End Time */}
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1.5" htmlFor="filter-end-time">
                {t('deviceLogSearch.endTime')}
              </label>
              <input
                id="filter-end-time"
                type="datetime-local"
                value={endTime}
                onChange={(e) => setEndTime(e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:ring-2"
              />
            </div>

            {/* Search button */}
            <div className="flex items-end">
              <button
                id="device-log-search-btn"
                onClick={handleSearch}
                disabled={loading || !deviceId}
                className="w-full flex items-center justify-center gap-2 px-5 py-2.5 text-sm font-semibold text-white rounded-xl disabled:opacity-50 transition-all"
                style={{
                  background: loading || !deviceId
                    ? '#c4b5fd'
                    : 'linear-gradient(135deg, #a855f7, #7c3aed)',
                  boxShadow: loading || !deviceId ? 'none' : '0 4px 12px rgba(168,85,247,0.35)',
                }}
              >
                {loading ? <Spinner /> : (
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                      d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                  </svg>
                )}
                {loading ? t('deviceLogSearch.searching') : t('deviceLogSearch.search')}
              </button>
            </div>
          </div>
        </div>

        {/* ── Error Banner ── */}
        {error && (
          <div
            className="flex items-start gap-3 px-4 py-3 rounded-xl mb-4 text-sm"
            style={{ background: '#fef2f2', border: '1px solid #fecaca', color: '#b91c1c' }}
          >
            <svg className="w-4 h-4 mt-0.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M12 9v2m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
            </svg>
            <span>{error}</span>
          </div>
        )}

        {/* ── Photo error ── */}
        {photoError && (
          <div
            className="flex items-start gap-3 px-4 py-3 rounded-xl mb-4 text-sm"
            style={{ background: '#fff7ed', border: '1px solid #fed7aa', color: '#c2410c' }}
          >
            <svg className="w-4 h-4 mt-0.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
            <span>{t('deviceLogSearch.photoError')}: {photoError}</span>
          </div>
        )}

        {/* ── Results ── */}
        {fetched && result && (
          <>
            {/* Summary bar */}
            <div
              className="flex flex-wrap items-center justify-between gap-3 px-5 py-3 rounded-xl mb-4"
              style={{ background: '#ede9fe', border: '1px solid #ddd6fe' }}
            >
              <div className="flex items-center gap-2">
                <span className="text-sm font-semibold" style={{ color: '#7c3aed' }}>
                  {t('deviceLogSearch.totalEvents', { n: result.totalMatches.toLocaleString() })}
                </span>
                {result.responseStatus === 'MORE' && (
                  <span
                    className="text-xs px-2 py-0.5 rounded-full font-medium"
                    style={{ background: '#a855f7', color: '#fff' }}
                  >
                    {t('deviceLogSearch.moreAvailable')}
                  </span>
                )}
              </div>
              <div className="flex items-center gap-2 text-xs" style={{ color: '#7c3aed' }}>
                <span>{t('deviceLogSearch.pageOf', { x: page + 1, y: totalPages })}</span>
                <span>·</span>
                <span>{t('deviceLogSearch.showingOnPage', { n: result.numOfMatches })}</span>
              </div>
            </div>

            {/* Results table */}
            {result.items.length === 0 ? (
              <div className="bg-white rounded-2xl shadow-sm p-14 text-center" style={{ border: '1px solid #f1f5f9' }}>
                <svg className="w-12 h-12 mx-auto mb-3 text-gray-200" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                    d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <p className="text-gray-400 text-sm">{t('deviceLogSearch.noResults')}</p>
                <p className="text-gray-300 text-xs mt-1">{t('deviceLogSearch.tryAdjustFilters')}</p>
              </div>
            ) : (
              <div className="bg-white rounded-2xl shadow-sm overflow-hidden" style={{ border: '1px solid #f1f5f9' }}>
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr style={{ background: '#faf5ff', borderBottom: '2px solid #ede9fe' }}>
                        <th className="text-left px-5 py-3.5 text-xs font-semibold text-gray-500 uppercase tracking-wider w-8">#</th>
                        <th className="text-left px-5 py-3.5 text-xs font-semibold text-gray-500 uppercase tracking-wider">{t('deviceLogSearch.employeeId')}</th>
                        <th className="text-left px-5 py-3.5 text-xs font-semibold text-gray-500 uppercase tracking-wider">{t('deviceLogSearch.name')}</th>
                        <th className="text-left px-5 py-3.5 text-xs font-semibold text-gray-500 uppercase tracking-wider">{t('deviceLogSearch.cardNo')}</th>
                        <th className="text-left px-5 py-3.5 text-xs font-semibold text-gray-500 uppercase tracking-wider">{t('deviceLogSearch.event')}</th>
                        <th className="text-left px-5 py-3.5 text-xs font-semibold text-gray-500 uppercase tracking-wider">{t('deviceLogSearch.verifyMode')}</th>
                        <th className="text-left px-5 py-3.5 text-xs font-semibold text-gray-500 uppercase tracking-wider">{t('deviceLogSearch.time')}</th>
                        <th className="text-center px-5 py-3.5 text-xs font-semibold text-gray-500 uppercase tracking-wider">{t('deviceLogSearch.photo')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {result.items.map((event, idx) => {
                        const rowNum = page * pageSize + idx + 1
                        const isLoadingThis = photoBtnLoading === event.pictureURL
                        return (
                          <tr
                            key={`${event.time}-${idx}`}
                            style={{ borderBottom: '1px solid #f8f5ff' }}
                            className="transition-colors"
                            onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.background = '#faf5ff' }}
                            onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.background = '' }}
                          >
                            {/* Row number */}
                            <td className="px-5 py-3.5 text-xs text-gray-400 font-mono">{rowNum}</td>

                            {/* Employee ID */}
                            <td className="px-5 py-3.5">
                              {event.employeeId ? (
                                <span
                                  className="inline-flex items-center px-2 py-0.5 rounded-md text-xs font-mono font-medium"
                                  style={{ background: '#ede9fe', color: '#7c3aed' }}
                                >
                                  {event.employeeId}
                                </span>
                              ) : (
                                <span className="text-gray-300 text-xs">—</span>
                              )}
                            </td>

                            {/* Name */}
                            <td className="px-5 py-3.5">
                              <span className="font-medium text-gray-900 text-sm">
                                {event.name || <span className="text-gray-300">—</span>}
                              </span>
                            </td>

                            {/* Card No */}
                            <td className="px-5 py-3.5 text-gray-500 font-mono text-xs">
                              {event.cardNo || <span className="text-gray-300">—</span>}
                            </td>

                            {/* Event description */}
                            <td className="px-5 py-3.5">
                              {(() => {
                                const badge = eventBadgeStyle(event.eventDescription)
                                return (
                                  <span
                                    className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium"
                                    style={{ background: badge.background, color: badge.color }}
                                  >
                                    <span
                                      className="w-1.5 h-1.5 rounded-full inline-block"
                                      style={{ background: badge.dot }}
                                    />
                                    {event.eventDescription || t('deviceLogSearch.event')}
                                  </span>
                                )
                              })()}
                            </td>

                            {/* Verify mode */}
                            <td className="px-5 py-3.5 text-gray-500 text-xs capitalize">
                              {event.verifyMode
                                ? event.verifyMode.replace(/([A-Z])/g, ' $1').trim()
                                : <span className="text-gray-300">—</span>
                              }
                            </td>

                            {/* Time */}
                            <td className="px-5 py-3.5 text-gray-700 text-xs whitespace-nowrap font-mono">
                              {event.time
                                ? new Date(event.time).toLocaleString([], {
                                    dateStyle: 'short',
                                    timeStyle: 'medium',
                                  })
                                : '—'}
                            </td>

                            {/* Photo — only when device returned a pictureURL */}
                            <td className="px-5 py-3.5 text-center">
                              {event.pictureURL ? (
                                <button
                                  id={`view-photo-btn-${idx}`}
                                  onClick={() => handleViewPhoto(event)}
                                  disabled={!!photoBtnLoading}
                                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-all disabled:opacity-50"
                                  style={{
                                    background: isLoadingThis ? '#ede9fe' : '#a855f7',
                                    color: isLoadingThis ? '#7c3aed' : '#fff',
                                    boxShadow: isLoadingThis ? 'none' : '0 2px 6px rgba(168,85,247,0.4)',
                                  }}
                                >
                                  {isLoadingThis ? (
                                    <>
                                      <Spinner />
                                      {t('common.loading')}
                                    </>
                                  ) : (
                                    <>
                                      <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                                          d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                                      </svg>
                                      {t('deviceLogSearch.viewPhoto')}
                                    </>
                                  )}
                                </button>
                              ) : (
                                <span className="text-xs text-gray-300">—</span>
                              )}
                            </td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>

                <PaginationBar
                  page={page}
                  pageSize={pageSize}
                  totalItems={result.totalMatches}
                  loading={loading}
                  onPageChange={handlePageChange}
                  onPageSizeChange={handlePageSizeChange}
                  idPrefix="pagination"
                />
              </div>
            )}
          </>
        )}

        {/* ── Empty state (before first search) ── */}
        {!fetched && !loading && (
          <div
            className="bg-white rounded-2xl p-14 text-center"
            style={{ border: '1px solid #f1f5f9', boxShadow: '0 1px 3px rgba(0,0,0,0.04)' }}
          >
            <div
              className="w-16 h-16 rounded-2xl mx-auto mb-4 flex items-center justify-center"
              style={{ background: 'linear-gradient(135deg, #ede9fe, #ddd6fe)' }}
            >
              <svg className="w-8 h-8" style={{ color: '#a855f7' }} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                  d="M15 10l4.553-2.069A1 1 0 0121 8.867V15.1a1 1 0 01-1.447.894L15 14M3 8a2 2 0 012-2h8a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2V8z" />
              </svg>
            </div>
            <p className="text-gray-700 font-semibold mb-1">{t('deviceLogSearch.emptyHint')}</p>
            <p className="text-gray-400 text-sm max-w-sm mx-auto">
              {t('deviceLogSearch.emptyHelp')}
            </p>
          </div>
        )}

        {/* ── Loading skeleton ── */}
        {loading && !fetched && (
          <div className="bg-white rounded-2xl shadow-sm overflow-hidden animate-pulse" style={{ border: '1px solid #f1f5f9' }}>
            {[...Array(6)].map((_, i) => (
              <div key={i} className="flex items-center gap-4 px-5 py-4" style={{ borderBottom: '1px solid #f8f5ff' }}>
                <div className="w-8 h-3 rounded bg-gray-100" />
                <div className="w-20 h-4 rounded bg-gray-100" />
                <div className="w-32 h-4 rounded bg-gray-100" />
                <div className="w-16 h-4 rounded bg-gray-100" />
                <div className="w-40 h-6 rounded-full bg-gray-100" />
                <div className="w-24 h-3 rounded bg-gray-100" />
                <div className="ml-auto w-20 h-7 rounded-lg bg-purple-50" />
              </div>
            ))}
          </div>
        )}
      </div>
    </Layout>
  )
}
