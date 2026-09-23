import { useEffect, useMemo, useState } from 'react'
import { employeeApi } from '../api/employeeApi.ts'
import { timetableApi } from '../api/timetableApi.ts'
import { shiftAssignmentApi } from '../api/shiftAssignmentApi.ts'
import { departmentApi } from '../api/departmentApi.ts'
import { Department, Employee, EmployeeShiftAssignment, Timetable } from '../types'
import { todayInAppTimeZone } from '../utils/dateTime.ts'

interface AssignmentPayload {
  employeeIds?: number[]
  departmentIds?: number[]
  timetableId: number
  startDate: string
  endDate?: string
}

interface ShiftAssignmentModalProps {
  employees: Employee[]
  departments: Department[]
  timetableId: number
  onClose: () => void
  onSave: (payload: AssignmentPayload) => Promise<void>
}

function ShiftAssignmentModal({ employees, departments, timetableId, onClose, onSave }: ShiftAssignmentModalProps) {
  const [selectionMode, setSelectionMode] = useState<'EMPLOYEE' | 'DEPARTMENT'>('EMPLOYEE')
  const [selectedEmployeeIds, setSelectedEmployeeIds] = useState<number[]>([])
  const [selectedDepartmentIds, setSelectedDepartmentIds] = useState<number[]>([])
  const [startDate, setStartDate] = useState(todayInAppTimeZone())
  const [endDate, setEndDate] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  const toggleEmployee = (employeeId: number, checked: boolean) => {
    setSelectedEmployeeIds(prev => checked ? [...prev, employeeId] : prev.filter(id => id !== employeeId))
  }

  const toggleDepartment = (departmentId: number, checked: boolean) => {
    setSelectedDepartmentIds(prev => checked ? [...prev, departmentId] : prev.filter(id => id !== departmentId))
  }

  const handleSave = async () => {
    const hasSelection = selectionMode === 'EMPLOYEE'
      ? selectedEmployeeIds.length > 0
      : selectedDepartmentIds.length > 0
    if (!hasSelection) {
      setError(selectionMode === 'EMPLOYEE' ? 'Ən azı bir əməkdaş seçin' : 'Ən azı bir departament seçin')
      return
    }
    if (endDate && endDate < startDate) {
      setError('Bitmə tarixi başlanğıc tarixindən kiçik ola bilməz')
      return
    }
    setSaving(true)
    setError('')
    try {
      await onSave({
        employeeIds: selectionMode === 'EMPLOYEE' ? selectedEmployeeIds : [],
        departmentIds: selectionMode === 'DEPARTMENT' ? selectedDepartmentIds : [],
        timetableId,
        startDate,
        endDate: endDate || undefined,
      })
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
        <div className="inline-flex w-fit rounded-lg border border-gray-200 bg-gray-50 p-1">
          <button
            type="button"
            onClick={() => setSelectionMode('EMPLOYEE')}
            className={`px-3 py-1.5 text-sm font-medium ${selectionMode === 'EMPLOYEE' ? 'rounded-md bg-white text-purple-700 shadow-sm' : 'text-gray-500'}`}
          >
            Əməkdaşlar
          </button>
          <button
            type="button"
            onClick={() => setSelectionMode('DEPARTMENT')}
            className={`px-3 py-1.5 text-sm font-medium ${selectionMode === 'DEPARTMENT' ? 'rounded-md bg-white text-purple-700 shadow-sm' : 'text-gray-500'}`}
          >
            Departamentlər
          </button>
        </div>
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
        <div className="max-h-64 space-y-1 overflow-y-auto rounded-lg border p-2">
          {selectionMode === 'EMPLOYEE' ? employees.map(employee => (
            <label key={employee.id} className="flex items-center gap-2 rounded px-2 py-1 text-sm hover:bg-gray-50">
              <input type="checkbox" checked={selectedEmployeeIds.includes(employee.id)} onChange={e => toggleEmployee(employee.id, e.target.checked)} />
              <span>{employee.employeeId} — {employee.firstName} {employee.lastName}</span>
            </label>
          )) : departments.map(department => {
            const employeeCount = employees.filter(employee => employee.departmentId === department.id).length
            return (
              <label key={department.id} className="flex items-center justify-between gap-3 rounded px-2 py-1.5 text-sm hover:bg-gray-50">
                <span className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={selectedDepartmentIds.includes(department.id)}
                    onChange={event => toggleDepartment(department.id, event.target.checked)}
                  />
                  <span>{department.departmentName}</span>
                </span>
                <span className="text-xs text-gray-400">{employeeCount}</span>
              </label>
            )
          })}
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
  const [departments, setDepartments] = useState<Department[]>([])
  const [assignments, setAssignments] = useState<EmployeeShiftAssignment[]>([])
  const [activeTimetableId, setActiveTimetableId] = useState<number | null>(null)
  const [search, setSearch] = useState('')
  const [sortBy, setSortBy] = useState<'name' | 'department' | 'position'>('name')
  const [loading, setLoading] = useState(false)
  const [showModal, setShowModal] = useState(false)

  const fetchData = async () => {
    setLoading(true)
    try {
      const [timetableRes, employeeRes, assignmentRes, departmentRes] = await Promise.all([
        timetableApi.getAll(),
        employeeApi.getAll(0, 500),
        shiftAssignmentApi.getAll(),
        departmentApi.getAll(),
      ])
      const timetableData = timetableRes.data?.data ?? []
      const employeeData = employeeRes.data?.content ?? []
      const assignmentData = assignmentRes.data?.data ?? []
      const departmentData = departmentRes.data?.data ?? []

      setTimetables(timetableData)
      setEmployees(employeeData)
      setAssignments(assignmentData)
      setDepartments(departmentData)
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

  const saveAssignment = async (payload: AssignmentPayload) => {
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
          departments={departments}
          timetableId={activeTimetableId}
          onClose={() => setShowModal(false)}
          onSave={saveAssignment}
        />
      )}
    </div>
  )
}
