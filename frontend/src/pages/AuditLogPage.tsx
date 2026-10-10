import { FormEvent, useEffect, useState } from 'react'
import Layout from '../components/Layout'
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
    return <details><summary className="cursor-pointer text-violet-700">{parsed.changes.length} sahə — bax</summary>
      <dl className="mt-2 space-y-2 min-w-48">{parsed.changes.map((c, i) => <div key={i}><dt className="font-medium">{fields[c.field] || c.field}</dt><dd className="break-words text-gray-600"><span>{c.before ?? '—'}</span> → <span>{c.after ?? '—'}</span></dd></div>)}</dl></details>
  } catch { return <span>{details}</span> }
}
const emptyFilters = { username: '', action: '', entityType: '', start: '', end: '' }
export default function AuditLogPage() {
  const [draft, setDraft] = useState(emptyFilters)
  const [query, setQuery] = useState({ ...emptyFilters, page: 0 })
  const [rows, setRows] = useState<AuditRow[]>([])
  const [pages, setPages] = useState(0)
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true
    setLoading(true); setError('')
    const params = Object.fromEntries(Object.entries(query).filter(([, v]) => v !== ''))
    client.get<{ data: { content: AuditRow[]; totalPages: number; totalElements: number } }>('/audit-logs', { params })
      .then(({ data }) => { if (active) { setRows(data.data.content); setPages(data.data.totalPages); setTotal(data.data.totalElements) } })
      .catch(e => { if (active) { setRows([]); setError(getApiErrorMessage(e, 'Jurnal yüklənmədi.')) } })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [query])
  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (draft.start && draft.end && draft.start > draft.end) { setError('Başlanğıc tarixi bitmə tarixindən sonra ola bilməz.'); return }
    setQuery({ ...draft, page: 0 })
  }
  return <Layout><div className="p-4 sm:p-6 space-y-5"><div><h1 className="text-2xl font-bold">Əməliyyat jurnalı</h1><p className="text-sm text-gray-500 mt-1">Kim, nə vaxt, nəyi dəyişib. Tarix və saat Bakı vaxtı ilədir.</p><p className="text-sm text-gray-500">Jurnal aktivləşdirildikdən sonrakı saxlanılmış dəyişikliklər göstərilir. Məxfi dəyərlər saxlanılmır. Avtomatik əməliyyatlar “Sistem” kimi görünür.</p></div>
    <form onSubmit={submit} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-6 rounded-xl border bg-white p-4">
      <label className="text-sm">İstifadəçi adı<input placeholder="Dəqiq istifadəçi adı" className="w-full mt-1 rounded border p-2" value={draft.username} onChange={e => setDraft({ ...draft, username: e.target.value })} /></label>
      <label className="text-sm">Əməliyyat<select className="w-full mt-1 rounded border p-2" value={draft.action} onChange={e => setDraft({ ...draft, action: e.target.value })}><option value="">Hamısı</option>{Object.entries(actions).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
      <label className="text-sm">Bölmə<select className="w-full mt-1 rounded border p-2" value={draft.entityType} onChange={e => setDraft({ ...draft, entityType: e.target.value })}><option value="">Hamısı</option>{Object.entries(entities).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
      <label className="text-sm">Başlanğıc<input type="date" className="w-full mt-1 rounded border p-2" value={draft.start} onChange={e => setDraft({ ...draft, start: e.target.value })} /></label>
      <label className="text-sm">Bitmə<input type="date" className="w-full mt-1 rounded border p-2" value={draft.end} onChange={e => setDraft({ ...draft, end: e.target.value })} /></label>
      <button disabled={loading} className="self-end rounded bg-violet-600 text-white p-2 disabled:opacity-50">Göstər / yenilə</button>
    </form>
    {error && <p role="alert" className="bg-red-50 text-red-700 p-3 rounded">{error}</p>}
    <div className="rounded-xl border bg-white overflow-x-auto"><table className="w-full text-sm"><thead className="bg-gray-50"><tr>{['Tarix / saat', 'İstifadəçi', 'Əməliyyat', 'Bölmə / qeyd', 'Dəyişiklik'].map(h => <th key={h} className="p-3 text-left">{h}</th>)}</tr></thead><tbody>{loading ? <tr><td colSpan={5} className="p-6">Yüklənir...</td></tr> : rows.length ? rows.map(r => <tr key={r.id} className="border-t align-top"><td className="p-3 whitespace-nowrap">{formatAttendanceDateTime(r.createdAt)}</td><td className="p-3">{r.username}</td><td className="p-3">{actions[r.action] || r.action}</td><td className="p-3">{entities[r.entityType] || r.entityType} #{r.entityId}</td><td className="p-3"><Changes details={r.details} /></td></tr>) : <tr><td colSpan={5} className="p-6 text-gray-500">Uyğun qeyd tapılmadı.</td></tr>}</tbody></table></div>
    <div className="flex flex-wrap justify-between items-center gap-3 text-sm"><span>{total} qeyd · Səhifə {query.page + 1} / {Math.max(1, pages)}</span><div className="flex gap-2"><button className="border rounded px-3 py-2 disabled:opacity-40" disabled={loading || query.page === 0} onClick={() => setQuery({ ...query, page: query.page - 1 })}>Əvvəlki</button><button className="border rounded px-3 py-2 disabled:opacity-40" disabled={loading || query.page + 1 >= pages} onClick={() => setQuery({ ...query, page: query.page + 1 })}>Növbəti</button></div></div>
  </div></Layout>
}
