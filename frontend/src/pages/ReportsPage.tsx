import { useEffect, useState, type ReactNode } from 'react'
import Layout from '../components/Layout.tsx'
import { attendanceApi } from '../api/attendanceApi.ts'
import { positionApi } from '../api/positionApi.ts'
import { departmentApi } from '../api/departmentApi.ts'
import { branchApi } from '../api/branchApi.ts'
import { timetableApi } from '../api/timetableApi.ts'
import { reportDateError, useAttendanceReportStore } from '../store/attendanceReportStore.ts'
import { toast } from '../store/toastStore.ts'
import { AttendanceOverviewFilters, AttendanceOverviewRow, AttendanceReportView, AttendanceReportSortField, Position, Department, Branch, Timetable } from '../types'
import { formatAttendanceDateTime, formatAttendanceTime, monthStartInAppTimeZone, todayInAppTimeZone } from '../utils/dateTime.ts'

const sections: { value: AttendanceReportView; label: string; description: string }[] = [
  { value: 'DETAILS', label: 'Giriş–çıxış detalları', description: 'Hər giriş–çıxış cütü ayrıca sətirdədir. Çıxışsız təkrar girişdə son giriş saatı göstərilir.' },
  { value: 'DAILY', label: 'Gündəlik', description: 'Hər əməkdaş üçün günün yekunu, gecikmə və erkən çıxış. Hər iki hal olduqda hər iki status göstərilir.' },
  { value: 'MONTHLY', label: 'Aylıq', description: 'Hər əməkdaşın aylar üzrə davamiyyəti və iş müddəti. Yalnız seçilmiş tarix aralığındakı günlər toplanır.' },
  { value: 'YEARLY', label: 'İllik', description: 'Hər əməkdaşın il üzrə yekunu. Yalnız seçilmiş tarix aralığındakı günlər toplanır.' },
]
const statusLabels: Record<string, string> = {
  LATE: 'Gecikib', EARLY_LEAVE: 'Erkən çıxıb', ABSENT: 'Gəlməyib', ON_LEAVE: 'Məzuniyyətdə',
  DAY_OFF: 'İstirahət günü', OPEN_SESSION: 'Çıxış yoxdur', OPEN: 'Çıxış yoxdur', MISSING_EXIT: 'Çıxış yoxdur',
  ON_PERMISSION: 'İcazədə', PERMITTED_EARLY_LEAVE: 'İcazəli erkən çıxış', PRESENT: 'İşdə',
  WORKDAY_COMPLETE: 'İş günü tamamlanıb', NOT_STARTED: 'İş vaxtı başlamayıb', CLOSED: 'Bağlanıb', MANUALLY_CORRECTED: 'Manual düzəliş',
}
const statusOptions = ['LATE', 'EARLY_LEAVE', 'LATE_AND_EARLY', 'PRESENT', 'ABSENT', 'MISSING_EXIT',
  'ON_LEAVE', 'DAY_OFF', 'ON_PERMISSION', 'PERMITTED_EARLY_LEAVE', 'WORKDAY_COMPLETE', 'NOT_STARTED']
const inputClass = 'w-full min-w-0 border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-purple-400'
const actionClass = 'px-3 py-2 border border-gray-200 rounded-lg text-sm font-medium hover:bg-gray-50 disabled:opacity-50'
const sortFields: Partial<Record<string, AttendanceReportSortField>> = {
  'Ad soyad': 'fullName', 'Status': 'status', 'Giriş': 'checkInTime', 'Çıxış': 'checkOutTime',
  'İş müddəti': 'workedMinutes', 'Əlavə iş vaxtı': 'overtimeMinutes', 'ID': 'employeeId', 'FIN': 'fin',
}
const descendingFirst = new Set<AttendanceReportSortField>(['checkInTime', 'checkOutTime', 'workedMinutes', 'overtimeMinutes'])

function duration(minutes?: number | null) {
  if (minutes == null) return '—'
  return `${Math.floor(minutes / 60)} saat ${minutes % 60} dəqiqə`
}
function statusClass(status: string) {
  if (status === 'LATE') return 'bg-amber-50 text-amber-800'
  if (status === 'EARLY_LEAVE') return 'bg-orange-50 text-orange-800'
  if (status === 'ABSENT' || status === 'MISSING_EXIT' || status === 'OPEN' || status === 'OPEN_SESSION') return 'bg-red-50 text-red-700'
  if (status.includes('PERMISSION') || status === 'ON_LEAVE' || status === 'PERMITTED_EARLY_LEAVE') return 'bg-purple-50 text-purple-700'
  if (status === 'DAY_OFF' || status === 'NOT_STARTED') return 'bg-gray-100 text-gray-600'
  return 'bg-emerald-50 text-emerald-700'
}
function Status({ row }: { row: AttendanceOverviewRow }) {
  const values = row.statuses?.length ? row.statuses : row.status ? [row.status] : []
  return <div className="flex flex-wrap gap-1 min-w-[120px]">{values.map(status => (
    <span key={status} className={`rounded-full px-2.5 py-1 text-xs font-medium ${statusClass(status)}`}>{statusLabels[status] ?? status}</span>
  ))}</div>
}
function Field({ name, label, children }: { name: string; label: string; children: ReactNode }) {
  return <div className="min-w-0"><label htmlFor={name} className="mb-1 block text-xs font-medium text-gray-600">{label}</label>{children}</div>
}

export default function ReportsPage() {
  const { rows, filters, page, totalPages, loading, error, totalElements, setFilters, setPage, fetchReport } = useAttendanceReportStore()
  const [positions, setPositions] = useState<Position[]>([])
  const [departments, setDepartments] = useState<Department[]>([])
  const [areas, setAreas] = useState<Branch[]>([])
  const [timetables, setTimetables] = useState<Timetable[]>([])
  const [exporting, setExporting] = useState(false)
  const dateError = reportDateError(filters)
  const detail = filters.view === 'DETAILS'
  const daily = filters.view === 'DAILY'
  const aggregate = !detail && !daily

  useEffect(() => {
    const timer = window.setTimeout(() => { void fetchReport() }, 250)
    return () => window.clearTimeout(timer)
  }, [filters, page, fetchReport])

  useEffect(() => {
    let active = true
    void Promise.all([positionApi.getAll(), departmentApi.getAll(), branchApi.getAll(), timetableApi.getAll()])
      .then(([pos, dept, area, timetable]) => {
        if (!active) return
        setPositions(pos.data.data ?? []); setDepartments(dept.data.data ?? [])
        setAreas(area.data.data ?? []); setTimetables(timetable.data.data ?? [])
      }).catch(() => { if (active) toast.error('Filtr seçimləri yüklənmədi', 'Səhifəni yeniləyib təkrar yoxlayın.') })
    return () => { active = false }
  }, [])

  const update = (patch: Partial<AttendanceOverviewFilters>) =>
    setFilters({ ...useAttendanceReportStore.getState().filters, ...patch })
  const nextSortDirection = (field: AttendanceReportSortField) => filters.sortBy === field
    ? filters.sortDirection === 'asc' ? 'desc' : 'asc' : descendingFirst.has(field) ? 'desc' : 'asc'
  const changeView = (view: AttendanceReportView) => {
    const hideSort = (view === 'MONTHLY' || view === 'YEARLY')
      && ['status', 'checkInTime', 'checkOutTime'].includes(filters.sortBy ?? '')
    update({ view, status: undefined, ...(hideSort ? { sortBy: undefined, sortDirection: undefined } : {}) })
  }
  const visibleDepartments = departments.filter(d => !filters.areaId || d.allAreas || d.branchId === filters.areaId)
  const visiblePositions = positions.filter(p => filters.departmentId ? p.departmentId === filters.departmentId
    : !filters.areaId || visibleDepartments.some(d => d.id === p.departmentId))
  const numeric = (value: string) => value ? Number(value) : undefined
  const exportExcel = async () => {
    setExporting(true)
    try {
      const res = await attendanceApi.exportOverview(filters)
      const url = URL.createObjectURL(new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }))
      const link = document.createElement('a'); link.href = url
      link.download = `davamiyyet_${filters.view.toLowerCase()}_${filters.start}_${filters.end}.xlsx`
      link.click(); URL.revokeObjectURL(url)
      toast.success('Excel faylı hazırlandı')
    } catch { toast.error('Excel faylı hazırlanmadı', 'Bir qədər sonra təkrar yoxlayın.') }
    finally { setExporting(false) }
  }

  const columns: { label: string; cell: (r: AttendanceOverviewRow) => ReactNode }[] = [
    ...(!detail ? [{ label: aggregate ? 'Dövr' : 'Tarix', cell: (r: AttendanceOverviewRow) => r.period }] : []),
    { label: 'Ad soyad', cell: r => <span className="font-medium text-gray-900">{r.fullName}</span> },
    ...(!aggregate ? [{ label: 'Status', cell: (r: AttendanceOverviewRow) => <Status row={r} /> }] : []),
    ...(!aggregate ? [
      { label: 'Giriş', cell: (r: AttendanceOverviewRow) => daily ? formatAttendanceTime(r.checkInTime) : formatAttendanceDateTime(r.checkInTime) },
      { label: 'Çıxış', cell: (r: AttendanceOverviewRow) => daily && (!r.checkOutTime || r.checkOutTime.slice(0, 10) === r.date)
        ? formatAttendanceTime(r.checkOutTime) : formatAttendanceDateTime(r.checkOutTime) },
    ] : []),
    { label: 'İş müddəti', cell: r => <span className="font-semibold text-gray-900">{duration(r.workedMinutes)}</span> },
    { label: 'Əlavə iş vaxtı', cell: r => <span className={r.overtimeMinutes ? 'font-semibold text-purple-700' : ''}>{duration(r.overtimeMinutes)}</span> },
    ...(!detail ? [
      { label: 'Gecikmə', cell: (r: AttendanceOverviewRow) => <span className={r.lateMinutes > 0 ? 'font-semibold text-amber-700' : ''}>{duration(r.lateMinutes)}</span> },
      { label: 'Erkən çıxış', cell: (r: AttendanceOverviewRow) => <span className={r.earlyLeaveMinutes > 0 ? 'font-semibold text-orange-700' : ''}>{duration(r.earlyLeaveMinutes)}</span> },
      { label: 'İcazə', cell: (r: AttendanceOverviewRow) => duration(r.permissionMinutes) },
    ] : [{ label: 'Tanıma metodu', cell: (r: AttendanceOverviewRow) => r.verificationMethod ?? '—' }]),
    ...(aggregate ? [
      { label: 'Gəldiyi gün', cell: (r: AttendanceOverviewRow) => r.attendedDays },
      { label: 'Gəlmədiyi gün', cell: (r: AttendanceOverviewRow) => r.absentDays },
      { label: 'Gecikdiyi gün', cell: (r: AttendanceOverviewRow) => r.lateDays },
      { label: 'Erkən çıxdığı gün', cell: (r: AttendanceOverviewRow) => r.earlyLeaveDays },
      { label: 'Məzuniyyət günü', cell: (r: AttendanceOverviewRow) => r.leaveDays },
      { label: 'Çıxışı olmayan gün', cell: (r: AttendanceOverviewRow) => r.missingExitDays },
    ] : []),
    { label: 'ID', cell: r => r.employeeId },
    { label: 'FIN', cell: r => r.fin ?? '—' },
    { label: 'Qrafik', cell: r => r.timetableName ?? '—' },
    { label: 'Ərazi', cell: r => r.area || '—' },
    { label: 'Departament', cell: r => r.department ?? '—' },
    { label: 'Vəzifə', cell: r => r.position ?? '—' },
  ]

  return <Layout>
    <div className="p-4 sm:p-8 space-y-5 bg-gray-50 min-h-screen">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div><h1 className="text-2xl font-bold text-gray-900">Davamiyyət hesabatları</h1>
          <p className="mt-1 text-sm text-gray-500 max-w-3xl">{sections.find(s => s.value === filters.view)?.description}</p>
          <p className="mt-1 text-xs text-gray-500 max-w-3xl">Əlavə iş vaxtı gündəlik qrafik normasından artıq işlənən müddətdir. Sərbəst qrafikdə norma olmadığı üçün hesablanmır.
            {detail && ' Günlük əlavə iş vaxtı yalnız günün son tamamlanmış giriş–çıxış sətrində göstərilir.'}</p></div>
        <div className="flex gap-2">
          <button className={actionClass} onClick={() => void fetchReport()} disabled={loading || !!dateError}>Yenilə</button>
          <button onClick={() => void exportExcel()} disabled={exporting || loading || !!dateError || !!error}
            className="px-4 py-2 rounded-lg text-sm font-medium text-white bg-purple-500 hover:bg-purple-600 disabled:opacity-50">{exporting ? 'Hazırlanır...' : 'Excel ixracı'}</button>
        </div>
      </div>
      <div role="tablist" aria-label="Hesabat bölmələri" className="flex flex-wrap gap-2 bg-white p-2 rounded-xl shadow-sm">
        {sections.map(section => <button role="tab" aria-selected={filters.view === section.value} key={section.value}
          onClick={() => changeView(section.value)}
          className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${filters.view === section.value
            ? 'bg-purple-500 text-white shadow-sm' : 'text-gray-600 hover:bg-gray-100'}`}>{section.label}</button>)}
      </div>
      <div className="bg-white rounded-xl shadow-sm p-4 space-y-4">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs font-medium text-gray-500 mr-1">Müddət aralığı</span>
          {[{ label: 'Bu gün', start: todayInAppTimeZone() }, { label: 'Bu ay', start: monthStartInAppTimeZone() },
            { label: 'Bu il', start: `${todayInAppTimeZone().slice(0, 4)}-01-01` }].map(preset =>
            <button key={preset.label} onClick={() => update({ start: preset.start, end: todayInAppTimeZone() })}
              className="px-3 py-1 text-xs rounded-full border border-gray-200 text-gray-600 hover:border-purple-300 hover:text-purple-700">{preset.label}</button>)}
          <button className="ml-auto text-xs text-purple-700 hover:underline" onClick={() => setFilters({ view: filters.view,
            start: filters.start, end: filters.end })}>Filtrləri təmizlə</button>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
          <Field name="report-start" label="Başlanğıc tarixi"><input id="report-start" type="date" value={filters.start}
            onChange={e => update({ start: e.target.value })} className={inputClass} aria-invalid={!!dateError} /></Field>
          <Field name="report-end" label="Bitmə tarixi"><input id="report-end" type="date" value={filters.end}
            onChange={e => update({ end: e.target.value })} className={inputClass} aria-invalid={!!dateError} />
            {dateError && <p role="alert" className="mt-1 text-xs text-red-600">{dateError}</p>}</Field>
          <Field name="report-name" label="Ad soyad"><input id="report-name" placeholder="Ad və ya soyad" value={filters.name ?? ''}
            onChange={e => update({ name: e.target.value })} className={inputClass} /></Field>
          <Field name="report-fin" label="FIN"><input id="report-fin" placeholder="FIN axtar" value={filters.fin ?? ''}
            onChange={e => update({ fin: e.target.value })} className={inputClass} /></Field>
          <Field name="report-timetable" label="Qrafik"><select id="report-timetable" value={filters.timetableId ?? ''}
            onChange={e => update({ timetableId: numeric(e.target.value) })} className={inputClass}>
            <option value="">Bütün qrafiklər</option>{timetables.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
          </select></Field>
          <Field name="report-area" label="Ərazi"><select id="report-area" value={filters.areaId ?? ''}
            onChange={e => update({ areaId: numeric(e.target.value), departmentId: undefined, positionId: undefined })} className={inputClass}>
            <option value="">Bütün ərazilər</option>{areas.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
          </select></Field>
          <Field name="report-department" label="Departament"><select id="report-department" value={filters.departmentId ?? ''}
            onChange={e => update({ departmentId: numeric(e.target.value), positionId: undefined })} className={inputClass}>
            <option value="">Bütün departamentlər</option>{visibleDepartments.map(d => <option key={d.id} value={d.id}>{d.departmentName}</option>)}
          </select></Field>
          <Field name="report-position" label="Vəzifə"><select id="report-position" value={filters.positionId ?? ''}
            onChange={e => update({ positionId: numeric(e.target.value) })} className={inputClass}>
            <option value="">Bütün vəzifələr</option>{visiblePositions.map(p => <option key={p.id} value={p.id}>{p.positionName}</option>)}
          </select></Field>
          {daily && <Field name="report-status" label="Status"><select id="report-status" value={filters.status ?? ''}
            onChange={e => update({ status: e.target.value || undefined })} className={inputClass}>
            <option value="">Bütün statuslar</option>{statusOptions.map(s => <option key={s} value={s}>{s === 'LATE_AND_EARLY' ? 'Həm gecikib, həm erkən çıxıb' : statusLabels[s]}</option>)}
          </select></Field>}
        </div>
      </div>
      {!detail && <p className="text-xs text-gray-500">Gecikmə və erkən çıxış qrafikin güzəşt müddəti və təsdiqlənmiş icazələr nəzərə alınaraq hesablanır. Gələcək günlər yekuna daxil edilmir.
        {filters.areaId && ' Ərazi filtri uyğun əməkdaş günlərini seçir; göstərilən saat həmin günün bütün ərazilər üzrə yekunudur.'}</p>}
      <div className="bg-white rounded-xl shadow-sm overflow-x-auto" role="region" aria-label="Hesabat cədvəli" tabIndex={0}>
        <table className="min-w-full text-sm"><thead className="bg-gray-50"><tr>{columns.map(c => {
          const field = sortFields[c.label]
          const active = field != null && filters.sortBy === field
          const next = field ? nextSortDirection(field) : 'asc'
          const directionLabel = field && descendingFirst.has(field)
            ? next === 'asc' ? 'azdan çoxa' : 'çoxdan aza' : next === 'asc' ? 'A–Z / artan' : 'Z–A / azalan'
          return <th scope="col" key={c.label} aria-sort={field ? active ? filters.sortDirection === 'asc' ? 'ascending' : 'descending' : 'none' : undefined}
            className="text-left px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wide whitespace-nowrap">
            {field ? <button type="button" onClick={() => update({ sortBy: field, sortDirection: next })}
              title={`${c.label}: ${directionLabel} sırala`} aria-label={`${c.label}: ${directionLabel} sırala`}
              className={`inline-flex items-center gap-2 rounded focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-purple-400 hover:text-purple-700 uppercase tracking-wide ${active ? 'text-purple-700' : ''}`}>
              {c.label}<span aria-hidden="true">{active ? filters.sortDirection === 'asc' ? '↑' : '↓' : '↕'}</span>
            </button> : c.label}
          </th>
        })}</tr></thead>
          <tbody>{loading ? <tr><td colSpan={columns.length} className="px-4 py-8 text-center text-gray-500">Yüklənir...</td></tr>
            : error ? <tr><td colSpan={columns.length} className="px-4 py-8 text-center text-red-600">{error} Yenilə düyməsi ilə təkrar yoxlayın.</td></tr>
            : rows.length === 0 ? <tr><td colSpan={columns.length} className="px-4 py-8 text-center text-gray-500">{dateError ? 'Tarix aralığını düzəldin.' : 'Seçilmiş filtrlərə uyğun məlumat tapılmadı.'}</td></tr>
            : rows.map((row, i) => <tr key={row.attendanceLogId ?? `${row.employeePk}-${row.period}-${i}`} className="border-t border-gray-100 hover:bg-gray-50">
              {columns.map(c => <td key={c.label} className="px-4 py-3 whitespace-nowrap text-gray-600">{c.cell(row)}</td>)}</tr>)}</tbody>
        </table>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-gray-500"><span>{totalElements} qeyd</span>
        <div className="flex gap-2 items-center"><button className={actionClass} disabled={loading || page <= 0} onClick={() => setPage(page - 1)}>Əvvəlki</button>
          <span>{page + 1} / {Math.max(totalPages, 1)}</span><button className={actionClass} disabled={loading || page + 1 >= totalPages} onClick={() => setPage(page + 1)}>Növbəti</button></div></div>
    </div>
  </Layout>
}
