import { FormEvent, useEffect, useState } from 'react'
import Layout from '../components/Layout'
import PaginationBar from '../components/PaginationBar'
import client from '../api/client'
import { getApiErrorMessage } from '../utils/apiError'
import { formatAttendanceDateTime } from '../utils/dateTime'

interface AuditRow { id: number; username: string; action: string; entityType: string; entityId: string; details: string; createdAt: string }
const entities: Record<string, string> = {
  Employee: 'Əməkdaş', Branch: 'Ərazi', Department: 'Departament', Position: 'Vəzifə', DeviceConfig: 'Cihaz', Door: 'Qapı',
  EmployeeArea: 'Əməkdaşın ərazisi', EmployeeDeviceAccess: 'Cihaz təyinatı', EmployeeShiftAssignment: 'Qrafik təyinatı',
  Timetable: 'Qrafik', TimetableDayRule: 'Qrafikin gün qaydası', EmployeePermission: 'İcazə', HolidayPermission: 'Bayram / istirahət',
  LeaveRequest: 'Məzuniyyət', FaceData: 'Əməkdaş fotosu', AttendanceLogAdjustment: 'Davamiyyət düzəlişi',
  EmployeeDeviceRemovalJob: 'Cihazdan silinmə işi', User: 'İstifadəçi', Tenant: 'Sistem parametrləri', BackupSettings: 'Backup parametrləri',
}
const actions: Record<string, string> = { CREATE: 'Əlavə edildi', UPDATE: 'Dəyişdirildi', DELETE: 'Silindi' }
const fields: Record<string, string> = {
  employeeId: 'Əməkdaş', firstName: 'Ad', lastName: 'Soyad', fatherName: 'Ata adı', branchId: 'Ərazi ID', departmentId: 'Departament ID', positionId: 'Vəzifə ID',
  hireDate: 'İşə qəbul tarixi', contractEndDate: 'Müqavilənin bitməsi', annualLeaveDuration: 'Məzuniyyət müddəti', annualLeaveBalance: 'Məzuniyyət qalığı',
  salary: 'Maaş', hourlyRate: 'Saatlıq tarif', shiftType: 'Qrafik növü', timetableId: 'Qrafik ID', employmentStatus: 'İş statusu',
  name: 'Ad', code: 'Kod', city: 'Şəhər', address: 'Ünvan', status: 'Status', isHeadOffice: 'Baş ofis', departmentName: 'Departament adı', positionName: 'Vəzifə adı',
  deviceName: 'Cihaz adı', deviceIp: 'IP', devicePort: 'Port', doorId: 'Qapı ID', doorRole: 'Keçid rolu', primary: 'Əsas ərazi',
  deviceConfigId: 'Cihaz ID', assignmentSource: 'Təyinat mənbəyi', sourceBranchId: 'Mənbə ərazi ID', effectiveStartDate: 'Başlanğıc tarixi', effectiveEndDate: 'Bitmə tarixi',
  startTime: 'Başlanğıc saatı', endTime: 'Bitmə saatı', crossesMidnight: 'Gecə yarısını keçir', allowedLateMinutes: 'Gecikmə güzəşti', allowedEarlyLeaveMinutes: 'Erkən çıxış güzəşti',
  breakMinutes: 'Fasilə dəqiqəsi', dayOfWeek: 'Həftənin günü', workingDay: 'İş günü', permissionTypeId: 'İcazə növü ID', startDate: 'Başlanğıc tarixi', endDate: 'Bitmə tarixi',
  deductFromWorkHours: 'İş saatından çıxılır', applyScope: 'Tətbiq dairəsi', leaveTypeId: 'Məzuniyyət növü ID', attendanceLogId: 'Keçid ID',
  previousCheckInTime: 'Əvvəlki giriş', previousCheckOutTime: 'Əvvəlki çıxış', newCheckInTime: 'Yeni giriş', newCheckOutTime: 'Yeni çıxış',
  completed: 'Tamamlandı', attempts: 'Cəhd sayı', username: 'İstifadəçi adı', userType: 'Rol', companyName: 'Şirkət adı', attendanceSyncIntervalMinutes: 'Sinxronizasiya intervalı',
  passwordHash: 'Parol', passwordEncrypted: 'Cihaz parolu', faceImageUrl: 'Foto', faceId: 'Foto identifikatoru', cardId: 'Kart', finNumber: 'FIN', deviceEmployeeNo: 'Terminal nömrəsi',
  birthDate: 'Doğum tarixi', gender: 'Cins', mobilePhone: 'Telefon', email: 'E-poçt', serialNumber: 'Seriya nömrəsi', contractNumber: 'Müqavilə nömrəsi', allowance: 'Əlavə ödəniş',
  emergencyContact: 'Əlaqələndirici', notes: 'Qeydlər', reason: 'Səbəb', description: 'Təsvir', enabled: 'Aktiv', folderPath: 'Backup qovluğu',
}
function Changes({ details }: { details: string }) {
  try {
    const parsed = JSON.parse(details) as { changes?: { field: string; before: string | null; after: string | null }[] }
    if (!Array.isArray(parsed.changes)) return <span>{details}</span>
    return <details><summary className="cursor-pointer text-purple-600 font-medium hover:text-purple-700">{parsed.changes.length} sahə — bax</summary>
      <dl className="mt-3 space-y-3 min-w-48 rounded-lg bg-gray-50 border border-gray-100 p-3">{parsed.changes.map((c, i) => <div key={i}><dt className="font-medium">{fields[c.field] || c.field}</dt><dd className="break-words text-xs text-gray-500 mt-1"><span>{c.before ?? '—'}</span> → <span>{c.after ?? '—'}</span></dd></div>)}</dl></details>
  } catch { return <span>{details}</span> }
}
const emptyFilters = { username: '', action: '', entityType: '', start: '', end: '' }
export default function AuditLogPage() {
  const [draft, setDraft] = useState(emptyFilters)
  const [query, setQuery] = useState({ ...emptyFilters, page: 0, size: 24 })
  const [rows, setRows] = useState<AuditRow[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true
    setLoading(true); setError('')
    const params = Object.fromEntries(Object.entries(query).filter(([, v]) => v !== ''))
    client.get<{ data: { content: AuditRow[]; totalPages: number; totalElements: number } }>('/audit-logs', { params })
      .then(({ data }) => { if (active) { setRows(data.data.content); setTotal(data.data.totalElements) } })
      .catch(e => { if (active) { setRows([]); setError(getApiErrorMessage(e, 'Jurnal yüklənmədi.')) } })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [query])
  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (draft.start && draft.end && draft.start > draft.end) { setError('Başlanğıc tarixi bitmə tarixindən sonra ola bilməz.'); return }
    setQuery({ ...draft, page: 0, size: query.size })
  }
  const inputClass = 'w-full min-w-0 mt-1.5 border border-gray-200 rounded-lg px-3 py-2 text-sm text-gray-700 bg-white focus:outline-none focus:ring-2 focus:ring-purple-500'
  return (
    <Layout>
      <div className="p-4 sm:p-8 bg-slate-50 min-h-screen">
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-gray-900">Əməliyyat jurnalı</h1>
          <p className="text-sm text-gray-500 mt-1">İstifadəçilərin etdiyi dəyişikliklər və əməliyyat tarixçəsi</p>
        </div>
        <form onSubmit={submit} className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 items-end bg-white rounded-xl shadow-sm p-5 mb-6">
          <label className="text-xs font-medium text-gray-500 min-w-0">İstifadəçi adı
            <input placeholder="Dəqiq istifadəçi adı" className={inputClass} value={draft.username} onChange={e => setDraft({ ...draft, username: e.target.value })} />
          </label>
          <label className="text-xs font-medium text-gray-500 min-w-0">Əməliyyat
            <select className={inputClass} value={draft.action} onChange={e => setDraft({ ...draft, action: e.target.value })}><option value="">Hamısı</option>{Object.entries(actions).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
          </label>
          <label className="text-xs font-medium text-gray-500 min-w-0">Bölmə
            <select className={inputClass} value={draft.entityType} onChange={e => setDraft({ ...draft, entityType: e.target.value })}><option value="">Hamısı</option>{Object.entries(entities).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
          </label>
          <label className="text-xs font-medium text-gray-500 min-w-0">Başlanğıc
            <input type="date" className={inputClass} value={draft.start} onChange={e => setDraft({ ...draft, start: e.target.value })} />
          </label>
          <label className="text-xs font-medium text-gray-500 min-w-0">Bitmə
            <input type="date" className={inputClass} value={draft.end} onChange={e => setDraft({ ...draft, end: e.target.value })} />
          </label>
          <button disabled={loading} className="flex items-center justify-center gap-2 px-5 py-2.5 text-sm font-medium text-white bg-purple-500 rounded-lg hover:bg-purple-600 disabled:opacity-50 transition-colors">
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" /></svg>
            {loading ? 'Yüklənir...' : 'Göstər / yenilə'}
          </button>
        </form>
        {error && <p role="alert" className="mb-4 px-4 py-3 rounded-lg border border-red-200 bg-red-50 text-sm text-red-700">{error}</p>}
        <div className="bg-white rounded-xl shadow-sm overflow-hidden">
          <div className="flex flex-wrap items-center justify-between gap-2 px-5 py-4 border-b border-gray-100">
            <h2 className="text-sm font-semibold text-gray-900">Dəyişikliklər <span className="ml-2 px-2 py-0.5 rounded-full bg-purple-50 text-purple-700 text-xs">{total}</span></h2>
            <span className="text-xs text-gray-400">Bakı vaxtı ilə</span>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-500"><tr>{['Tarix / saat', 'İstifadəçi', 'Əməliyyat', 'Bölmə / qeyd', 'Dəyişiklik'].map(h => <th key={h} className="px-5 py-3 text-left text-xs font-medium whitespace-nowrap">{h}</th>)}</tr></thead>
              <tbody>
                {loading ? <tr><td colSpan={5} className="p-10 text-center text-gray-400">Yüklənir...</td></tr> : rows.length ? rows.map(r => (
                  <tr key={r.id} className="border-t border-gray-100 align-top hover:bg-gray-50 transition-colors">
                    <td className="px-5 py-3 whitespace-nowrap text-gray-500">{formatAttendanceDateTime(r.createdAt)}</td>
                    <td className="px-5 py-3 font-medium text-gray-900">{r.username}</td>
                    <td className="px-5 py-3"><span className={`inline-block whitespace-nowrap px-2 py-0.5 rounded-full text-xs font-medium ${r.action === 'CREATE' ? 'bg-emerald-100 text-emerald-800' : r.action === 'DELETE' ? 'bg-red-100 text-red-800' : 'bg-purple-100 text-purple-700'}`}>{actions[r.action] || r.action}</span></td>
                    <td className="px-5 py-3 text-gray-700">{entities[r.entityType] || r.entityType} <span className="text-gray-400">#{r.entityId}</span></td>
                    <td className="px-5 py-3"><Changes details={r.details} /></td>
                  </tr>
                )) : <tr><td colSpan={5} className="p-10 text-center text-gray-400">Uyğun qeyd tapılmadı.</td></tr>}
              </tbody>
            </table>
          </div>
          <PaginationBar page={query.page} pageSize={query.size} totalItems={total} loading={loading} idPrefix="audit-log" onPageChange={page => setQuery({ ...query, page })} onPageSizeChange={size => setQuery({ ...query, size, page: 0 })} />
        </div>
        <p className="text-xs text-gray-400 mt-4">Jurnal aktivləşdirildikdən sonrakı dəyişikliklər göstərilir. Məxfi dəyərlər saxlanılmır. Avtomatik əməliyyatlar “Sistem” kimi görünür.</p>
      </div>
    </Layout>
  )
}
