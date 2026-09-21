import { useEffect, useMemo, useState } from 'react'
import { employeeApi } from '../api/employeeApi.ts'
import { permissionApi } from '../api/permissionApi.ts'
import { employeePermissionApi } from '../api/employeePermissionApi.ts'
import { Employee, EmployeePermission, PermissionType } from '../types'
import { statusLabel } from '../i18n/labels.ts'

interface PermissionModalProps {
  employees: Employee[]
  permissionTypeId: number
  onClose: () => void
  onSave: (payload: {
    employeeIds: number[]
    permissionTypeId: number
    startDate: string
    endDate: string
    reason?: string
    status?: string
  }) => Promise<void>
}

function PermissionAssignmentModal({ employees, permissionTypeId, onClose, onSave }: PermissionModalProps) {
  const [selectedEmployeeIds, setSelectedEmployeeIds] = useState<number[]>([])
  const [startDate, setStartDate] = useState(new Date().toISOString().split('T')[0])
  const [endDate, setEndDate] = useState(new Date().toISOString().split('T')[0])
  const [reason, setReason] = useState('')
  const [status, setStatus] = useState('PENDING')
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
    if (endDate < startDate) {
      setError('Bitmə tarixi başlanğıc tarixindən kiçik ola bilməz')
      return
    }
    setSaving(true)
    setError('')
    try {
      await onSave({ employeeIds: selectedEmployeeIds, permissionTypeId, startDate, endDate, reason, status })
      onClose()
    } catch (e: unknown) {
      setError((e as Error).message || 'İcazə təyini alınmadı')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-2xl shadow-xl p-6 w-full max-w-xl space-y-4">
        <h3 className="text-lg font-semibold text-gray-900">İcazə təyin et</h3>
        {error && <p className="text-sm text-red-500">{error}</p>}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div>
            <label className="text-sm text-gray-700">Başlanğıc tarixi</label>
            <input type="date" value={startDate} onChange={e => setStartDate(e.target.value)} className="w-full border rounded-lg px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="text-sm text-gray-700">Bitmə tarixi</label>
            <input type="date" value={endDate} onChange={e => setEndDate(e.target.value)} className="w-full border rounded-lg px-3 py-2 text-sm" />
          </div>
          <div className="md:col-span-2">
            <label className="text-sm text-gray-700">Qeyd/Səbəb</label>
            <textarea value={reason} onChange={e => setReason(e.target.value)} className="w-full border rounded-lg px-3 py-2 text-sm" rows={2} />
          </div>
          <div>
            <label className="text-sm text-gray-700">Vəziyyət</label>
            <select value={status} onChange={e => setStatus(e.target.value)} className="w-full border rounded-lg px-3 py-2 text-sm">
              <option value="PENDING">{statusLabel('PENDING')}</option>
              <option value="APPROVED">{statusLabel('APPROVED')}</option>
              <option value="ACTIVE">{statusLabel('ACTIVE')}</option>
            </select>
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

export default function PermissionAssignmentPage() {
  const [employees, setEmployees] = useState<Employee[]>([])
  const [permissionTypes, setPermissionTypes] = useState<PermissionType[]>([])
  const [permissions, setPermissions] = useState<EmployeePermission[]>([])
  const [activePermissionTypeId, setActivePermissionTypeId] = useState<number | null>(null)
  const [search, setSearch] = useState('')
  const [sortBy, setSortBy] = useState<'name' | 'department' | 'startDate'>('name')
  const [showModal, setShowModal] = useState(false)

  const fetchData = async () => {
    const [employeeRes, typeRes, permissionRes] = await Promise.all([
      employeeApi.getAll(0, 500),
      permissionApi.getTypes(),
      employeePermissionApi.getAll(),
    ])

    const employeeData = employeeRes.data?.content ?? []
    const typeData = typeRes.data?.data ?? []
    const permissionData = permissionRes.data?.data ?? []

    setEmployees(employeeData)
    setPermissionTypes(typeData)
    setPermissions(permissionData)
    if (typeData.length && !activePermissionTypeId) {
      setActivePermissionTypeId(typeData[0].id)
    }
  }

  useEffect(() => {
    void fetchData()
  }, [])

  const filteredPermissions = useMemo(() => {
    if (!activePermissionTypeId) return []

    const rows = permissions
      .filter(permission => permission.permissionTypeId === activePermissionTypeId)
      .map(permission => ({ permission, employee: employees.find(employee => employee.id === permission.employeeId) }))
      .filter(item => !!item.employee)
      .filter(item => {
        if (!search.trim()) return true
        const text = `${item.employee?.employeeId} ${item.employee?.firstName} ${item.employee?.lastName} ${item.employee?.departmentName}`.toLowerCase()
        return text.includes(search.toLowerCase())
      })

    return rows.sort((a, b) => {
      if (sortBy === 'department') {
        return (a.employee?.departmentName || '').localeCompare(b.employee?.departmentName || '')
      }
      if (sortBy === 'startDate') {
        return a.permission.startDate.localeCompare(b.permission.startDate)
      }
      return `${a.employee?.firstName} ${a.employee?.lastName}`.localeCompare(`${b.employee?.firstName} ${b.employee?.lastName}`)
    })
  }, [activePermissionTypeId, employees, permissions, search, sortBy])

  const removePermission = async (id: number) => {
    await employeePermissionApi.remove(id)
    await fetchData()
  }

  const savePermission = async (payload: {
    employeeIds: number[]
    permissionTypeId: number
    startDate: string
    endDate: string
    reason?: string
    status?: string
  }) => {
    await employeePermissionApi.bulkGrant(payload)
    await fetchData()
  }

  const exportCsv = () => {
    const rows = filteredPermissions.map(item => [
      item.employee?.employeeId,
      `${item.employee?.firstName} ${item.employee?.lastName}`,
      item.employee?.departmentName || '',
      item.permission.startDate,
      item.permission.endDate,
      statusLabel(item.permission.status),
      item.permission.reason || '',
    ])
    const csv = [['Əməkdaş ID', 'Ad', 'Departament', 'Başlanğıc tarixi', 'Bitiş tarixi', 'Vəziyyət', 'Səbəb'], ...rows]
      .map(row => row.map(cell => `"${cell ?? ''}"`).join(','))
      .join('\n')
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'employee-permissions.csv'
    link.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-bold text-gray-900">İcazə təyin</h2>
          <p className="text-sm text-gray-500">Əməkdaşlar üçün icazələri idarə edin</p>
        </div>
        <div className="flex gap-2">
          <button onClick={exportCsv} className="px-3 py-2 text-sm rounded-lg border text-gray-700">CSV yüklə</button>
          <button
            onClick={() => setShowModal(true)}
            disabled={!activePermissionTypeId}
            className="px-3 py-2 text-sm rounded-lg text-white disabled:opacity-60"
            style={{ background: '#a855f7' }}
          >
            İcazə ver
          </button>
        </div>
      </div>

      <div className="flex flex-wrap gap-2 border-b border-gray-200">
        {permissionTypes.map(type => (
          <button
            key={type.id}
            onClick={() => setActivePermissionTypeId(type.id)}
            className={`px-3 py-2 text-sm border-b-2 ${activePermissionTypeId === type.id ? 'border-purple-500 text-purple-600' : 'border-transparent text-gray-500'}`}
          >
            {type.name}
          </button>
        ))}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
        <input
          placeholder="Əməkdaş/departament axtar..."
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="border rounded-lg px-3 py-2 text-sm"
        />
        <select value={sortBy} onChange={e => setSortBy(e.target.value as 'name' | 'department' | 'startDate')} className="border rounded-lg px-3 py-2 text-sm">
          <option value="name">Ada görə</option>
          <option value="department">Departamentə görə</option>
          <option value="startDate">Başlanğıc tarixinə görə</option>
        </select>
      </div>

      <div className="bg-white border rounded-xl overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-600">
            <tr>
              <th className="text-left px-4 py-3">Əməkdaş</th>
              <th className="text-left px-4 py-3">Departament</th>
              <th className="text-left px-4 py-3">Tarix aralığı</th>
              <th className="text-left px-4 py-3">Vəziyyət</th>
              <th className="text-left px-4 py-3">Qeyd</th>
              <th className="text-right px-4 py-3">Əməliyyat</th>
            </tr>
          </thead>
          <tbody>
            {filteredPermissions.length === 0 ? (
              <tr><td colSpan={6} className="px-4 py-8 text-center text-gray-500">Bu icazə növü üçün qeyd yoxdur</td></tr>
            ) : filteredPermissions.map(item => (
              <tr key={item.permission.id} className="border-t">
                <td className="px-4 py-3">{item.employee?.employeeId} — {item.employee?.firstName} {item.employee?.lastName}</td>
                <td className="px-4 py-3">{item.employee?.departmentName || '—'}</td>
                <td className="px-4 py-3">{item.permission.startDate} → {item.permission.endDate}</td>
                <td className="px-4 py-3">{statusLabel(item.permission.status)}</td>
                <td className="px-4 py-3">{item.permission.reason || '—'}</td>
                <td className="px-4 py-3 text-right">
                  <button onClick={() => void removePermission(item.permission.id)} className="text-red-500 hover:text-red-700">Sil</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {showModal && activePermissionTypeId && (
        <PermissionAssignmentModal
          employees={employees.filter(employee => employee.employmentStatus === 'ACTIVE')}
          permissionTypeId={activePermissionTypeId}
          onClose={() => setShowModal(false)}
          onSave={savePermission}
        />
      )}
    </div>
  )
}
