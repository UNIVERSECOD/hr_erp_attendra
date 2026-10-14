import { useEffect, useMemo, useState } from 'react'
import { leaveApi } from '../api/leaveApi.ts'
import { Employee, LeaveRequest, LeaveType } from '../types'
import { getApiErrorMessage } from '../utils/apiError.ts'
import { toast } from '../store/toastStore.ts'

type Props = { employees: Employee[]; onChanged: () => Promise<void> }

export default function LeaveManagement({ employees, onChanged }: Props) {
  const [types, setTypes] = useState<LeaveType[]>([])
  const [leaves, setLeaves] = useState<LeaveRequest[]>([])
  const [employeeId, setEmployeeId] = useState<number | ''>('')
  const [typeId, setTypeId] = useState<number | ''>('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [status, setStatus] = useState<'APPROVED' | 'PENDING'>('APPROVED')
  const [deductFromLeaveBalance, setDeductFromLeaveBalance] = useState(false)
  const [open, setOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const employeeMap = useMemo(() => new Map(employees.map(employee => [employee.id, employee])), [employees])
  const typeMap = useMemo(() => new Map(types.map(type => [type.id, type])), [types])
  const selectedEmployee = employeeId === '' ? undefined : employeeMap.get(employeeId)

  const reload = async () => {
    const [leaveResponse, typeResponse] = await Promise.all([leaveApi.getAll(), leaveApi.getTypes()])
    setLeaves(leaveResponse.data.data ?? [])
    setTypes(typeResponse.data.data ?? [])
  }

  useEffect(() => {
    void reload().catch((cause: unknown) => setError(getApiErrorMessage(cause, 'Məzuniyyət məlumatları yüklənmədi')))
  }, [])

  const save = async () => {
    if (!employeeId || !typeId || !startDate || !endDate || endDate < startDate) {
      setError('Əməkdaşı, məzuniyyət növünü və düzgün tarix aralığını seçin.')
      return
    }
    setSaving(true)
    setError('')
    try {
      await leaveApi.create({ employeeId, leaveTypeId: typeId, startDate, endDate, status, deductFromLeaveBalance })
      await Promise.all([reload(), onChanged()])
      setOpen(false)
      setEmployeeId('')
      setTypeId('')
      setStartDate('')
      setEndDate('')
      setDeductFromLeaveBalance(false)
      toast.success(status === 'APPROVED' ? 'Məzuniyyət təsdiqləndi' : 'Məzuniyyət gözləməyə alındı')
    } catch (cause: unknown) {
      const message = getApiErrorMessage(cause, 'Məzuniyyət əlavə edilmədi')
      setError(message)
      toast.error('Məzuniyyət əlavə edilmədi', message)
    } finally {
      setSaving(false)
    }
  }

  const changeStatus = async (leave: LeaveRequest, next: 'APPROVED' | 'REJECTED') => {
    try {
      await leaveApi.updateStatus(leave.id, next)
      await Promise.all([reload(), onChanged()])
      toast.success(next === 'APPROVED' ? 'Məzuniyyət təsdiqləndi' : 'Məzuniyyət rədd edildi')
    } catch (cause: unknown) {
      toast.error('Məzuniyyət statusu dəyişmədi', getApiErrorMessage(cause, 'Xəta baş verdi'))
    }
  }

  const remove = async (leave: LeaveRequest) => {
    if (!window.confirm('Bu məzuniyyət qeydi silinsin? Balansdan çıxılmış günlər varsa, geri qaytarılacaq.')) return
    try {
      await leaveApi.remove(leave.id)
      await Promise.all([reload(), onChanged()])
      toast.success('Məzuniyyət silindi')
    } catch (cause: unknown) {
      toast.error('Məzuniyyət silinmədi', getApiErrorMessage(cause, 'Xəta baş verdi'))
    }
  }

  return (
    <section className="space-y-4 rounded-lg border border-slate-200 bg-white p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold text-slate-900">Məzuniyyət</h2>
          <p className="text-sm text-slate-500">Təsdiqlənmiş məzuniyyət Tabeldə M göstərilir. Balans yalnız ayrıca seçim edilərsə azalır.</p>
        </div>
        <button type="button" onClick={() => setOpen(value => !value)} disabled={types.length === 0}
          className="rounded-md bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-50">
          + Məzuniyyət artır
        </button>
      </div>
      {error && <p role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
      {!error && types.length === 0 && <p className="text-sm text-amber-700">Məzuniyyət növləri tapılmadı.</p>}
      {open && (
        <div className="grid gap-3 rounded-md border border-emerald-100 bg-emerald-50 p-4 md:grid-cols-2">
          <label className="text-sm">Əməkdaş
            <select value={employeeId} onChange={event => setEmployeeId(event.target.value ? Number(event.target.value) : '')} className="mt-1 w-full rounded-md border px-3 py-2">
              <option value="">Seçin</option>
              {employees.filter(employee => employee.employmentStatus === 'ACTIVE').map(employee =>
                <option key={employee.id} value={employee.id}>{employee.firstName} {employee.lastName} ({employee.employeeId})</option>)}
            </select>
          </label>
          <label className="text-sm">Məzuniyyət növü
            <select value={typeId} onChange={event => setTypeId(event.target.value ? Number(event.target.value) : '')} className="mt-1 w-full rounded-md border px-3 py-2">
              <option value="">Seçin</option>
              {types.map(type => <option key={type.id} value={type.id}>{type.leaveName}</option>)}
            </select>
          </label>
          <label className="text-sm">Başlanğıc tarixi
            <input type="date" value={startDate} onChange={event => setStartDate(event.target.value)} className="mt-1 w-full rounded-md border px-3 py-2" />
          </label>
          <label className="text-sm">Bitmə tarixi
            <input type="date" value={endDate} onChange={event => setEndDate(event.target.value)} className="mt-1 w-full rounded-md border px-3 py-2" />
          </label>
          <label className="text-sm">Vəziyyət
            <select value={status} onChange={event => setStatus(event.target.value as 'APPROVED' | 'PENDING')} className="mt-1 w-full rounded-md border px-3 py-2">
              <option value="APPROVED">Təsdiqlə</option><option value="PENDING">Gözləmədə</option>
            </select>
          </label>
          <div className="flex items-center gap-2 text-sm text-slate-700">
            <input id="deduct-leave-balance" type="checkbox" checked={deductFromLeaveBalance}
              onChange={event => setDeductFromLeaveBalance(event.target.checked)} />
            <label htmlFor="deduct-leave-balance">Balansdan çıxılsın</label>
          </div>
          <p className="text-sm text-slate-600 md:col-span-2">
            {selectedEmployee && `Cari məzuniyyət balansı: ${selectedEmployee.annualLeaveBalance ?? 0} gün. `}
            Seçim aktivdirsə, təsdiq zamanı tarix aralığındakı bütün təqvim günləri (başlanğıc və bitiş daxil) balansdan çıxılır.
          </p>
          <div className="md:col-span-2 flex justify-end gap-2">
            <button type="button" onClick={() => setOpen(false)} className="rounded-md border px-4 py-2 text-sm">Ləğv et</button>
            <button type="button" onClick={() => void save()} disabled={saving} className="rounded-md bg-emerald-600 px-4 py-2 text-sm text-white disabled:opacity-50">{saving ? 'Yadda saxlanılır...' : 'Yadda saxla'}</button>
          </div>
        </div>
      )}
      <div className="overflow-x-auto">
        <table className="min-w-full text-left text-sm">
          <thead className="bg-slate-50 text-slate-600"><tr><th className="px-3 py-2">Əməkdaş</th><th className="px-3 py-2">Növ</th><th className="px-3 py-2">Tarix</th><th className="px-3 py-2">Vəziyyət</th><th className="px-3 py-2">Balansdan</th><th className="px-3 py-2">Əməliyyat</th></tr></thead>
          <tbody>{[...leaves].sort((a, b) => b.startDate.localeCompare(a.startDate)).map(leave => {
            const employee = employeeMap.get(leave.employeeId)
            return <tr key={leave.id} className="border-t">
              <td className="px-3 py-2">{employee ? `${employee.firstName} ${employee.lastName}` : `#${leave.employeeId}`}</td>
              <td className="px-3 py-2">{typeMap.get(leave.leaveTypeId)?.leaveName ?? '—'}</td>
              <td className="px-3 py-2">{leave.startDate === leave.endDate ? leave.startDate : `${leave.startDate} – ${leave.endDate}`}</td>
              <td className="px-3 py-2">{leave.status === 'APPROVED' ? 'Təsdiqlənib' : leave.status === 'PENDING' ? 'Gözləmədə' : 'Rədd edilib'}</td>
              <td className="px-3 py-2">{leave.deductFromLeaveBalance
                ? (leave.status === 'APPROVED' ? `${leave.balanceChargedDays ?? 0} gün` : 'Seçilib, gözləyir')
                : 'Xeyr'}</td>
              <td className="px-3 py-2 whitespace-nowrap">
                {leave.status !== 'APPROVED' && <button type="button" onClick={() => void changeStatus(leave, 'APPROVED')} className="mr-2 text-emerald-700">Təsdiqlə</button>}
                {leave.status === 'APPROVED' && <button type="button" onClick={() => void changeStatus(leave, 'REJECTED')} className="mr-2 text-amber-700">Rədd et</button>}
                <button type="button" onClick={() => void remove(leave)} className="text-red-600">Sil</button>
              </td>
            </tr>
          })}</tbody>
        </table>
        {leaves.length === 0 && <p className="p-4 text-center text-sm text-slate-500">Məzuniyyət qeydi yoxdur</p>}
      </div>
    </section>
  )
}
