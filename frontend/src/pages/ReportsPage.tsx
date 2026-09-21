import { useEffect, useState } from 'react'
import Layout from '../components/Layout.tsx'
import { attendanceApi } from '../api/attendanceApi.ts'
import { positionApi } from '../api/positionApi.ts'
import { departmentApi } from '../api/departmentApi.ts'
import { employeeApi } from '../api/employeeApi.ts'
import { useAttendanceReportStore } from '../store/attendanceReportStore.ts'
import { t } from '../i18n/index.ts'
import { Position, Department } from '../types'
import { formatAttendanceDateTime } from '../utils/dateTime.ts'

const SHIFT_TABS = [
  { labelKey: 'reports.allShifts' as const, value: '' },
  { labelKey: 'reports.standardShift' as const, value: 'STANDARD' },
  { labelKey: 'reports.freeShift' as const, value: 'FLEXIBLE' },
]

function formatDateTime(value?: string) {
  return formatAttendanceDateTime(value)
}

function formatDuration(minutes?: number) {
  if (minutes == null || minutes <= 0) return '—'
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60
  return `${String(hours).padStart(2, '0')}:${String(mins).padStart(2, '0')}`
}

function methodLabel(method?: string) {
  if (!method) return '—'
  switch (method.toLowerCase()) {
    case 'face':
      return 'face'
    case 'card':
      return 'card'
    case 'finger':
      return 'finger'
    case 'device':
      return 'device'
    default:
      return method
  }
}

function methodStyle(method?: string): { background: string; color: string } {
  switch ((method ?? '').toLowerCase()) {
    case 'face':
      return { background: '#ede9fe', color: '#5b21b6' }
    case 'card':
      return { background: '#dbeafe', color: '#1e40af' }
    case 'finger':
      return { background: '#dcfce7', color: '#166534' }
    default:
      return { background: '#f1f5f9', color: '#475569' }
  }
}

export default function ReportsPage() {
  const {
    rows, filters, page, totalPages, loading, totalElements, setFilters, setPage, fetchReport,
  } = useAttendanceReportStore()

  const [positions, setPositions] = useState<Position[]>([])
  const [departments, setDepartments] = useState<Department[]>([])
  const [areas, setAreas] = useState<string[]>([])

  useEffect(() => {
    void fetchReport()
  }, [filters, page, fetchReport])

  useEffect(() => {
    const loadLookups = async () => {
      const [posRes, deptRes, areaRes] = await Promise.allSettled([
        positionApi.getAll(),
        departmentApi.getAll(),
        employeeApi.getDistinctAreas(),
      ])
      if (posRes.status === 'fulfilled') setPositions(posRes.value.data.data ?? [])
      if (deptRes.status === 'fulfilled') setDepartments(deptRes.value.data.data ?? [])
      if (areaRes.status === 'fulfilled') setAreas(areaRes.value.data.data ?? [])
    }
    void loadLookups()
  }, [])

  const updateFilter = (name: string, value: string) => {
    setFilters({ ...filters, [name]: value })
  }

  const exportExcel = async () => {
    const res = await attendanceApi.exportExcel(filters)
    const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const href = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = href
    a.download = 'attendance_reports.xlsx'
    a.click()
    URL.revokeObjectURL(href)
  }

  return (
    <Layout>
      <div className="p-4 sm:p-8 space-y-5" style={{ background: '#f8fafc', minHeight: '100vh' }}>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Davamiyyət hesabatları</h1>
            <p className="mt-1 text-sm text-gray-500">{t('reports.subtitle')}</p>
          </div>
          <div className="flex items-center gap-3">
            <span className="text-sm text-gray-500">{t('reports.records', { n: totalElements })}</span>
            <button
              onClick={exportExcel}
              className="px-4 py-2 rounded-lg text-sm font-medium text-white"
              style={{ background: '#a855f7' }}
            >
              {t('reports.excelExport')}
            </button>
          </div>
        </div>

        <div className="flex flex-wrap gap-2 bg-white p-2 rounded-xl shadow-sm">
          {SHIFT_TABS.map((tab) => (
            <button
              key={tab.value || 'all'}
              onClick={() => setFilters({ ...filters, shiftType: tab.value })}
              className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                filters.shiftType === tab.value
                  ? 'text-white shadow-sm'
                  : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
              }`}
              style={filters.shiftType === tab.value ? { background: '#a855f7' } : undefined}
            >
              {t(tab.labelKey)}
            </button>
          ))}
        </div>

        <div className="bg-white rounded-xl shadow-sm p-4 grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-gray-500">{t('common.startDate')}</label>
            <input type="date" value={filters.start} onChange={(e) => updateFilter('start', e.target.value)} className="border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-400 w-full" />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-gray-500">{t('common.endDate')}</label>
            <input type="date" value={filters.end} onChange={(e) => updateFilter('end', e.target.value)} className="border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-400 w-full" />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-gray-500">ID axtar</label>
            <input placeholder="ID axtar" value={filters.employeeId ?? ''} onChange={(e) => updateFilter('employeeId', e.target.value)} className="border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-400 w-full" />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-gray-500">Ad soyad</label>
            <input placeholder="Ad soyad" value={filters.name ?? ''} onChange={(e) => updateFilter('name', e.target.value)} className="border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-400 w-full" />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-gray-500">FIN</label>
            <input placeholder="FIN" value={filters.fin ?? ''} onChange={(e) => updateFilter('fin', e.target.value)} className="border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-400 w-full" />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-gray-500">{t('reports.position')}</label>
            <select
              value={filters.position ?? ''}
              onChange={(e) => updateFilter('position', e.target.value)}
              className="border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-400 w-full bg-white"
            >
              <option value="">Hamısı (Vəzifə)</option>
              {positions.map((p) => (
                <option key={p.id} value={p.positionName}>{p.positionName}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-gray-500">{t('reports.department')}</label>
            <select
              value={filters.department ?? ''}
              onChange={(e) => updateFilter('department', e.target.value)}
              className="border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-400 w-full bg-white"
            >
              <option value="">Hamısı (Departament)</option>
              {departments.map((d) => (
                <option key={d.id} value={d.departmentName}>{d.departmentName}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-gray-500">{t('reports.area')}</label>
            <select
              value={filters.area ?? ''}
              onChange={(e) => updateFilter('area', e.target.value)}
              className="border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-400 w-full bg-white"
            >
              <option value="">Hamısı (Ərazi)</option>
              {areas.map((a) => (
                <option key={a} value={a}>{a}</option>
              ))}
            </select>
          </div>
        </div>

        <div className="bg-white rounded-xl shadow-sm overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead className="bg-gray-50">
              <tr>
                {[
                  'ID',
                  'Ad Soyad',
                  'FIN',
                  t('reports.department'),
                  t('reports.position'),
                  t('reports.area'),
                  t('reports.checkIn'),
                  t('reports.checkOut'),
                  t('reports.workedDuration'),
                  t('reports.recognitionMethod'),
                ].map((h) => (
                  <th key={h} className="text-left px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wide whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={10} className="px-4 py-6 text-sm text-gray-500 text-center">Yüklənir...</td></tr>
              ) : rows.length === 0 ? (
                <tr><td colSpan={10} className="px-4 py-6 text-sm text-gray-500 text-center">Məlumat tapılmadı</td></tr>
              ) : rows.map((row, index) => (
                <tr key={row.attendanceLogId ?? `${row.employeePk}-${row.checkInTime}-${index}`} className="border-t border-gray-100 hover:bg-gray-50">
                  <td className="px-4 py-3 whitespace-nowrap text-gray-700">{row.employeeId}</td>
                  <td className="px-4 py-3 whitespace-nowrap font-medium text-gray-900">{row.fullName}</td>
                  <td className="px-4 py-3 whitespace-nowrap text-gray-600">{row.fin ?? '—'}</td>
                  <td className="px-4 py-3 whitespace-nowrap" style={{ color: '#7c3aed' }}>{row.department ?? '—'}</td>
                  <td className="px-4 py-3 whitespace-nowrap" style={{ color: '#7c3aed' }}>{row.position ?? '—'}</td>
                  <td className="px-4 py-3 whitespace-nowrap text-gray-600">{row.area ?? '—'}</td>
                  <td className="px-4 py-3 whitespace-nowrap text-gray-800">{formatDateTime(row.checkInTime)}</td>
                  <td className="px-4 py-3 whitespace-nowrap text-gray-800">{formatDateTime(row.checkOutTime)}</td>
                  <td className="px-4 py-3 whitespace-nowrap font-semibold text-gray-900">{formatDuration(row.workedMinutes)}</td>
                  <td className="px-4 py-3 whitespace-nowrap">
                    <span
                      className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium"
                      style={methodStyle(row.verificationMethod)}
                    >
                      {methodLabel(row.verificationMethod)}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-3">
          <span className="text-sm text-gray-500">{t('reports.records', { n: totalElements })}</span>
          <div className="flex items-center gap-2">
            <button disabled={page <= 0} onClick={() => setPage(page - 1)} className="px-3 py-1.5 border border-gray-200 rounded-lg text-sm disabled:opacity-50 hover:bg-gray-50">{t('reports.prev')}</button>
            <span className="px-2 py-1 text-sm text-gray-600">{page + 1} / {Math.max(totalPages, 1)}</span>
            <button disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)} className="px-3 py-1.5 border border-gray-200 rounded-lg text-sm disabled:opacity-50 hover:bg-gray-50">{t('reports.next')}</button>
          </div>
        </div>
      </div>
    </Layout>
  )
}
