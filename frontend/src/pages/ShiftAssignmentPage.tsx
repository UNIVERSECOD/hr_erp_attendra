import { useEffect, useMemo, useState } from 'react'
import { employeeApi } from '../api/employeeApi.ts'
import { timetableApi } from '../api/timetableApi.ts'
import { shiftAssignmentApi } from '../api/shiftAssignmentApi.ts'
import { Employee, EmployeeShiftAssignment, Timetable } from '../types'

interface ShiftAssignmentModalProps {
  employees: Employee[]
  timetableId: number
  onClose: () => void
  onSave: (payload: { employeeIds: number[]; timetableId: number; startDate: string; endDate?: string }) => Promise<void>
}

function ShiftAssignmentModal({ employees, timetableId, onClose, onSave }: ShiftAssignmentModalProps) {
  const [selectedEmployeeIds, setSelectedEmployeeIds] = useState<number[]>([])
  const [startDate, setStartDate] = useState(new Date().toISOString().split('T')[0])
  const [endDate, setEndDate] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  const toggleEmployee = (employeeId: number, checked: boolean) => {
    setSelectedEmployeeIds(prev => checked ? [...prev, employeeId] : prev.filter(id => id !== employeeId))
  }

  const handleSave = async () => {
    if (!selectedEmployeeIds.length) {
      setError('Ən azı bir əməkdaş seçin')
      return
    }
    if (endDate && endDate < startDate) {
      setError('Bitmə tarixi başlanğıc tarixindən kiçik ola bilməz')
      return
    }
    setSaving(true)
    setError('')
    try {
      await onSave({ employeeIds: selectedEmployeeIds, timetableId, startDate, endDate: endDate || undefined })
      onClose()
    } catch (e: unknown) {
      setError((e as Error).message || 'Təyin etmə alınmadı')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-2xl shadow-xl p-6 w-full max-w-xl space-y-4">
        <h3 className="text-lg font-semibold text-gray-900">Növbə təyin et</h3>
        {error && <p className="text-sm text-red-500">{error}</p>}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div>
            <label className="text-sm text-gray-700">Başlanğıc tarixi</label>
            <input type="date" value={startDate} onChange={e => setStartDate(e.target.value)} className="w-full border rounded-lg px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="text-sm text-gray-700">Bitmə tarixi (opsional)</label>
            <input type="date" value={endDate} onChange={e => setEndDate(e.target.value)} className="w-full border rounded-lg px-3 py-2 text-sm" />
          </div>
        </div>
        <div className="max-h-64 overflow-y-auto border rounded-lg p-2 space-y-1">
          {employees.map(employee => (
            <label key={employee.id} className="flex items-center gap-2 px-2 py-1 rounded hover:bg-gray-50 text-sm">
              <input type="checkbox" checked={selectedEmployeeIds.includes(employee.id)} onChange={e => toggleEmployee(employee.id, e.target.checked)} />
              <span>{employee.employeeId} — {employee.firstName} {employee.lastName}</span>
            </label>
          ))}
        </div>
        <div className="flex justify-end gap-2">
          <button onClick={onClose} className="px-4 py-2 border rounded-lg text-gray-600">Ləğv et</button>
          <button onClick={handleSave} disabled={saving} className="px-4 py-2 rounded-lg text-white disabled:opacity-60" style={{ background: '#a855f7' }}>
            {saving ? 'Yüklənir...' : 'Təyin et'}
          </button>
        </div>
      </div>
    </div>
  )
}

export default function ShiftAssignmentPage() {
  const [timetables, setTimetables] = useState<Timetable[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [assignments, setAssignments] = useState<EmployeeShiftAssignment[]>([])
  const [activeTimetableId, setActiveTimetableId] = useState<number | null>(null)
  const [search, setSearch] = useState('')
  const [sortBy, setSortBy] = useState<'name' | 'department' | 'position'>('name')
  const [loading, setLoading] = useState(false)
  const [showModal, setShowModal] = useState(false)

  const fetchData = async () => {
    setLoading(true)
    try {
      const [timetableRes, employeeRes, assignmentRes] = await Promise.all([
        timetableApi.getAll(),
        employeeApi.getAll(0, 500),
        shiftAssignmentApi.getAll(),
      ])
      const timetableData = timetableRes.data?.data ?? []
      const employeeData = employeeRes.data?.content ?? []
      const assignmentData = assignmentRes.data?.data ?? []

      setTimetables(timetableData)
      setEmployees(employeeData)
      setAssignments(assignmentData)
      if (timetableData.length && !activeTimetableId) {
        setActiveTimetableId(timetableData[0].id)
      }
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void fetchData()
  }, [])

  const activeAssignments = useMemo(() => {
    if (!activeTimetableId) return []
    const filtered = assignments
      .filter(item => item.timetableId === activeTimetableId && item.status === 'ACTIVE')
      .map(item => ({
        assignment: item,
        employee: employees.find(employee => employee.id === item.employeeId),
      }))
      .filter(item => !!item.employee)
      .filter(item => {
        if (!search.trim()) return true
        const key = `${item.employee?.employeeId} ${item.employee?.firstName} ${item.employee?.lastName}`.toLowerCase()
        return key.includes(search.toLowerCase())
      })

    return filtered.sort((a, b) => {
      if (sortBy === 'department') {
        return (a.employee?.departmentName || '').localeCompare(b.employee?.departmentName || '')
      }
      if (sortBy === 'position') {
        return (a.employee?.positionName || '').localeCompare(b.employee?.positionName || '')
      }
      return `${a.employee?.firstName} ${a.employee?.lastName}`.localeCompare(`${b.employee?.firstName} ${b.employee?.lastName}`)
    })
  }, [activeTimetableId, assignments, employees, search, sortBy])

  const exportCsv = () => {
    const rows = activeAssignments.map(item => [
      item.employee?.employeeId,
      `${item.employee?.firstName} ${item.employee?.lastName}`,
      item.employee?.departmentName || '',
      item.employee?.positionName || '',
      item.assignment.effectiveStartDate,
      item.assignment.effectiveEndDate || '',
    ])
    const csv = [['Əməkdaş ID', 'Ad', 'Departament', 'Vəzifə', 'Başlanğıc tarixi', 'Bitiş tarixi'], ...rows]
      .map(row => row.map(cell => `"${cell ?? ''}"`).join(','))
      .join('\n')
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'shift-assignments.csv'
    link.click()
    URL.revokeObjectURL(url)
  }

  const removeAssignment = async (id: number) => {
    await shiftAssignmentApi.remove(id)
    await fetchData()
  }

  const saveAssignment = async (payload: { employeeIds: number[]; timetableId: number; startDate: string; endDate?: string }) => {
    await shiftAssignmentApi.bulkAssign(payload)
    await fetchData()
  }

  const assignableEmployees = employees.filter(employee => employee.employmentStatus === 'ACTIVE')

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-bold text-gray-900">Növbə təyin</h2>
          <p className="text-sm text-gray-500">Əməkdaşların növbələrə təyin edilməsini idarə edin</p>
        </div>
        <div className="flex gap-2">
          <button onClick={exportCsv} className="px-3 py-2 text-sm rounded-lg border text-gray-700">CSV yüklə</button>
          <button
            onClick={() => setShowModal(true)}
            disabled={!activeTimetableId}
            className="px-3 py-2 text-sm rounded-lg text-white disabled:opacity-60"
            style={{ background: '#a855f7' }}
          >
            Əməkdaş əlavə et
          </button>
        </div>
      </div>

      <div className="flex flex-wrap gap-2 border-b border-gray-200">
        {timetables.map(timetable => (
          <button
            key={timetable.id}
            onClick={() => setActiveTimetableId(timetable.id)}
            className={`px-3 py-2 text-sm border-b-2 ${activeTimetableId === timetable.id ? 'border-purple-500 text-purple-600' : 'border-transparent text-gray-500'}`}
          >
            {timetable.name}
          </button>
        ))}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
        <input
          placeholder="Əməkdaş axtar..."
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="border rounded-lg px-3 py-2 text-sm"
        />
        <select value={sortBy} onChange={e => setSortBy(e.target.value as 'name' | 'department' | 'position')} className="border rounded-lg px-3 py-2 text-sm">
          <option value="name">Ada görə</option>
          <option value="department">Departamentə görə</option>
          <option value="position">Vəzifəyə görə</option>
        </select>
      </div>

      <div className="bg-white border rounded-xl overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-600">
            <tr>
              <th className="text-left px-4 py-3">Əməkdaş</th>
              <th className="text-left px-4 py-3">Departament</th>
              <th className="text-left px-4 py-3">Vəzifə</th>
              <th className="text-left px-4 py-3">Tarix aralığı</th>
              <th className="text-right px-4 py-3">Əməliyyat</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={5} className="px-4 py-8 text-center text-gray-500">Yüklənir...</td></tr>
            ) : activeAssignments.length === 0 ? (
              <tr><td colSpan={5} className="px-4 py-8 text-center text-gray-500">Bu növbə üçün təyin edilmiş əməkdaş yoxdur</td></tr>
            ) : activeAssignments.map(item => (
              <tr key={item.assignment.id} className="border-t">
                <td className="px-4 py-3">{item.employee?.employeeId} — {item.employee?.firstName} {item.employee?.lastName}</td>
                <td className="px-4 py-3">{item.employee?.departmentName || '—'}</td>
                <td className="px-4 py-3">{item.employee?.positionName || '—'}</td>
                <td className="px-4 py-3">{item.assignment.effectiveStartDate} {item.assignment.effectiveEndDate ? `→ ${item.assignment.effectiveEndDate}` : '→ Açıq'}</td>
                <td className="px-4 py-3 text-right">
                  <button onClick={() => void removeAssignment(item.assignment.id)} className="text-red-500 hover:text-red-700">Sil</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {showModal && activeTimetableId && (
        <ShiftAssignmentModal
          employees={assignableEmployees}
          timetableId={activeTimetableId}
          onClose={() => setShowModal(false)}
          onSave={saveAssignment}
        />
      )}
    </div>
  )
}
