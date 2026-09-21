import { useEffect, useState } from 'react'
import Layout from '../components/Layout.tsx'
import { Department, Employee } from '../types'
import { departmentApi } from '../api/departmentApi.ts'
import { employeeApi } from '../api/employeeApi.ts'
import { useDebounce } from '../hooks/useSearch.ts'
import { useBranchStore } from '../store/branchStore.ts'

interface DepartmentFormData {
  departmentName: string
  description: string
  branchId: number | ''
  parentDepartmentId: number | ''
}

const defaultForm: DepartmentFormData = {
  departmentName: '',
  description: '',
  branchId: '',
  parentDepartmentId: '',
}

export default function DepartmentsPage() {
  const [departments, setDepartments] = useState<Department[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showModal, setShowModal] = useState(false)
  const [editingDept, setEditingDept] = useState<Department | null>(null)
  const [form, setForm] = useState<DepartmentFormData>(defaultForm)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [deleteConfirm, setDeleteConfirm] = useState<Department | null>(null)
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebounce(search, 300)
  const [parentSearch, setParentSearch] = useState('')
  const [selectedBranchId, setSelectedBranchId] = useState<number | ''>('')
  const { branches, fetchBranches } = useBranchStore()

  // Assign employees modal
  const [assignDept, setAssignDept] = useState<Department | null>(null)
  const [allEmployees, setAllEmployees] = useState<Employee[]>([])
  const [assignSearch, setAssignSearch] = useState('')
  const [selectedEmployeeIds, setSelectedEmployeeIds] = useState<Set<number>>(new Set())
  const [loadingEmployees, setLoadingEmployees] = useState(false)
  const [assignSaving, setAssignSaving] = useState(false)

  const fetchDepartments = async (branchId?: number) => {
    setLoading(true)
    setError(null)
    try {
      const res = await departmentApi.getAll(branchId)
      setDepartments(res.data?.data ?? [])
    } catch {
      setError('Departamentlər yüklənərkən xəta baş verdi.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchDepartments()
    fetchBranches()
  }, [fetchBranches])

  useEffect(() => {
    fetchDepartments(selectedBranchId === '' ? undefined : selectedBranchId)
  }, [selectedBranchId])

  const openCreate = () => {
    setEditingDept(null)
    setForm({ ...defaultForm, branchId: selectedBranchId })
    setParentSearch('')
    setFormError(null)
    setShowModal(true)
  }

  const openEdit = (dept: Department) => {
    setEditingDept(dept)
    setForm({
      departmentName: dept.departmentName,
      description: dept.description || '',
      branchId: dept.branchId || '',
      parentDepartmentId: dept.parentDepartmentId || '',
    })
    setParentSearch(dept.parentDepartmentName || '')
    setFormError(null)
    setShowModal(true)
  }

  const handleSave = async () => {
    if (!form.departmentName.trim()) {
      setFormError('Departament adı mütləqdir.')
      return
    }
    if (!form.branchId) {
      setFormError('Filial seçilməsi mütləqdir.')
      return
    }
    setSaving(true)
    setFormError(null)
    try {
      const payload = {
        departmentName: form.departmentName,
        description: form.description,
        branchId: Number(form.branchId),
        parentDepartmentId: form.parentDepartmentId ? Number(form.parentDepartmentId) : undefined,
      }
      if (editingDept) {
        await departmentApi.update(editingDept.id, payload)
      } else {
        await departmentApi.create(payload)
      }
      setShowModal(false)
      await fetchDepartments(selectedBranchId === '' ? undefined : selectedBranchId)
    } catch (e: unknown) {
      setFormError((e as Error).message || 'Saxlamaq alınmadı')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    if (!deleteConfirm) return
    try {
      await departmentApi.delete(deleteConfirm.id)
      setDeleteConfirm(null)
      await fetchDepartments()
    } catch {
      // ignore
    }
  }

  const openAssign = async (dept: Department) => {
    setAssignDept(dept)
    setAssignSearch('')
    setLoadingEmployees(true)
    try {
      const res = await employeeApi.getAll(0, 200)
      const employees = (res.data as { content?: Employee[] })?.content ?? []
      setAllEmployees(employees)
      const preChecked = new Set(
        employees.filter(e => e.departmentId === dept.id).map(e => e.id)
      )
      setSelectedEmployeeIds(preChecked)
    } catch {
      setAllEmployees([])
      setSelectedEmployeeIds(new Set())
    } finally {
      setLoadingEmployees(false)
    }
  }

  const toggleEmployee = (id: number) => {
    setSelectedEmployeeIds(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const handleAssignComplete = async () => {
    if (!assignDept) return
    setAssignSaving(true)
    try {
      // For newly selected employees, set their departmentId
      const toAssign = allEmployees.filter(e => selectedEmployeeIds.has(e.id) && e.departmentId !== assignDept.id)
      await Promise.all(toAssign.map(e => employeeApi.update(e.id, { departmentId: assignDept.id })))
      setAssignDept(null)
      await fetchDepartments()
    } catch {
      // ignore
    } finally {
      setAssignSaving(false)
    }
  }

  const filteredParents = departments.filter(d =>
    (!editingDept || d.id !== editingDept.id) &&
    d.departmentName.toLowerCase().includes(parentSearch.toLowerCase())
  )

  const filteredEmployees = allEmployees.filter(e =>
    `${e.firstName} ${e.lastName}`.toLowerCase().includes(assignSearch.toLowerCase()) ||
    (e.positionName || '').toLowerCase().includes(assignSearch.toLowerCase())
  )

  const filteredDepartments = debouncedSearch
    ? departments.filter(d => d.departmentName.toLowerCase().includes(debouncedSearch.toLowerCase()))
    : departments

  return (
    <Layout>
      <div className="p-4 sm:p-8" style={{ background: '#f4f5fb', minHeight: '100vh' }}>
        {/* Header */}
        <div className="flex flex-wrap items-start justify-between gap-3 mb-6">
          <div>
            <h1 className="text-2xl font-bold" style={{ color: '#1e2a4a' }}>Bütün departamentlər</h1>
            <p className="text-sm text-gray-500 mt-1">
              {departments.length} departament - işçilərin təşkili üçün ierarxik strukturlar
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => fetchDepartments(selectedBranchId === '' ? undefined : selectedBranchId)}
              className="flex items-center gap-1.5 px-3 py-2 text-sm font-medium text-gray-600 bg-white border border-gray-200 rounded-lg hover:bg-gray-50"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
              Yenilə
            </button>
            <button
              onClick={openCreate}
              className="flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-white rounded-lg"
              style={{ background: '#a855f7' }}
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
              </svg>
              + Departament əlavə et
            </button>
          </div>
        </div>

        {/* Search */}
        {!loading && !error && (
          <div className="mb-4 flex flex-col md:flex-row gap-2">
            <input
              type="text"
              placeholder="Departament axtar..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-purple-300"
            />
            <select
              value={selectedBranchId}
              onChange={(e) => setSelectedBranchId(e.target.value ? Number(e.target.value) : '')}
              className="w-full md:w-64 border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-purple-300"
            >
              <option value="">Bütün filiallar</option>
              {branches.map((branch) => (
                <option key={branch.id} value={branch.id}>{branch.name}</option>
              ))}
            </select>
          </div>
        )}

        {/* Column headers */}
        {!loading && !error && filteredDepartments.length > 0 && (
          <div className="flex items-center px-5 mb-2 text-xs font-semibold text-gray-400 uppercase tracking-wide">
            <div className="flex-1 min-w-0">Departament</div>
            <div className="w-32 text-center">Valideyn</div>
            <div className="w-36 text-center">Təyin edilmiş əməkdaşlar</div>
            <div className="w-44 text-center">Qaydalar</div>
            <div className="w-40 text-center">Təyin et</div>
            <div className="w-24 text-center">Ərazi</div>
            <div className="w-20 text-center">Əməliyyatlar</div>
          </div>
        )}

        {/* Content */}
        {loading ? (
          <div className="bg-white rounded-xl shadow-sm p-12 text-center text-gray-400">
            <div className="w-8 h-8 border-2 border-purple-300 border-t-purple-600 rounded-full animate-spin mx-auto mb-3"></div>
            Yüklənir...
          </div>
        ) : error ? (
          <div className="bg-white rounded-xl shadow-sm p-8 text-center text-red-500">{error}</div>
        ) : filteredDepartments.length === 0 ? (
          <div className="bg-white rounded-xl shadow-sm p-12 text-center text-gray-400">
            <svg className="w-12 h-12 mx-auto mb-3 text-gray-200" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
            </svg>
            {debouncedSearch ? `"${debouncedSearch}" üçün nəticə tapılmadı.` : 'Hələ heç bir departament yoxdur.'}
          </div>
        ) : (
          <div className="flex flex-col gap-3">
            {filteredDepartments.map((dept) => (
              <div key={dept.id} className="bg-white rounded-xl shadow-sm flex items-center px-5 py-4 gap-4">
                {/* Icon + Name + Description */}
                <div className="flex items-center gap-3 flex-1 min-w-0">
                  <div className="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0" style={{ background: '#ede9fe' }}>
                    <svg className="w-5 h-5" style={{ color: '#a855f7' }} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" />
                    </svg>
                  </div>
                  <div className="min-w-0">
                    <div className="font-semibold text-sm" style={{ color: '#1e2a4a' }}>{dept.departmentName}</div>
                    <div className="text-xs text-gray-400 truncate max-w-xs">{dept.description || 'Təsvir yoxdur'}</div>
                  </div>
                </div>

                {/* Parent */}
                <div className="w-32 text-center text-sm text-gray-600">
                  {dept.parentDepartmentName || <span className="text-gray-400 text-xs">Ən üst səviyyə</span>}
                </div>

                {/* Assigned Employees */}
                <div className="w-36 text-center">
                  <span className="text-sm font-semibold" style={{ color: '#1e2a4a' }}>{dept.employeeCount ?? 0}</span>
                  <span className="text-xs text-gray-400 ml-1">nəfər</span>
                </div>

                {/* Rules */}
                <div className="w-44 text-center">
                  <div className="text-xs text-gray-600">
                    {dept.calculateOvertime ? 'Əlavə iş aktiv' : 'Əlavə iş deaktiv'}
                  </div>
                  <div className="text-xs text-gray-600">
                    {dept.flexShift ? 'Sərbəst Növbə' : 'Standart Növbə'}
                  </div>
                </div>

                {/* Assign button */}
                <div className="w-40 flex justify-center">
                  <button
                    onClick={() => openAssign(dept)}
                    className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-white rounded-full"
                    style={{ background: '#a855f7' }}
                  >
                    <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" />
                    </svg>
                    Əməkdaşları təyin et
                  </button>
                </div>

                {/* Area */}
                <div className="w-24 text-center text-xs text-gray-500">0 devices</div>

                {/* Edit + Delete */}
                <div className="w-20 flex items-center justify-end gap-1">
                  <button
                    onClick={() => openEdit(dept)}
                    className="p-1.5 rounded-lg hover:bg-purple-50 transition-colors"
                    title="Redaktə et"
                  >
                    <svg className="w-4 h-4" style={{ color: '#a855f7' }} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                    </svg>
                  </button>
                  <button
                    onClick={() => setDeleteConfirm(dept)}
                    className="p-1.5 rounded-lg hover:bg-red-50 transition-colors"
                    title="Sil"
                  >
                    <svg className="w-4 h-4 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                    </svg>
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Create/Edit Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-end z-50">
          <div className="bg-white h-full w-full max-w-lg shadow-2xl flex flex-col overflow-y-auto">
            {/* Modal header */}
            <div className="flex items-center justify-between px-6 py-5 border-b border-gray-100" style={{ background: '#1e2a4a' }}>
              <h2 className="text-lg font-bold text-white">
                {editingDept ? 'Departamenti redaktə et' : 'Departament əlavə et'}
              </h2>
              <button onClick={() => setShowModal(false)} className="text-white opacity-70 hover:opacity-100">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <div className="flex-1 p-6 space-y-5">
              {formError && (
                <div className="bg-red-50 border border-red-200 text-red-700 px-3 py-2 rounded-lg text-sm">{formError}</div>
              )}

              {/* Branch */}
              <div>
                <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1">Filial *</label>
                <select
                  value={form.branchId}
                  onChange={(e) => setForm({ ...form, branchId: e.target.value ? Number(e.target.value) : '' })}
                  className="w-full border border-gray-200 rounded-lg px-3 py-2.5 focus:outline-none focus:ring-2 text-sm"
                >
                  <option value="">Seçin...</option>
                  {branches.map(b => (
                    <option key={b.id} value={b.id}>{b.name}</option>
                  ))}
                </select>
              </div>

              {/* Department Name */}
              <div>
                <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1">Departament adı *</label>
                <input
                  type="text"
                  value={form.departmentName}
                  onChange={(e) => setForm({ ...form, departmentName: e.target.value })}
                  placeholder="məs., Mühəndislik"
                  className="w-full border border-gray-200 rounded-lg px-3 py-2.5 focus:outline-none focus:ring-2 text-sm"
                />
              </div>

              {/* Description */}
              <div>
                <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1">Təsvir</label>
                <textarea
                  value={form.description}
                  onChange={(e) => setForm({ ...form, description: e.target.value })}
                  rows={3}
                  placeholder="Departament üçün qısa təsvir..."
                  className="w-full border border-gray-200 rounded-lg px-3 py-2.5 focus:outline-none focus:ring-2 text-sm resize-none"
                />
              </div>

              {/* Parent Department */}
              <div>
                <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1">Valideyn departament</label>
                <div className="relative">
                  <input
                    type="text"
                    value={parentSearch}
                    onChange={e => { setParentSearch(e.target.value); if (!e.target.value) setForm({ ...form, parentDepartmentId: '' }) }}
                    placeholder="Axtarın və ya valideyn seçin..."
                    className="w-full border border-gray-200 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2"
                  />
                  {parentSearch && filteredParents.length > 0 && (
                    <div className="absolute z-10 w-full bg-white border border-gray-200 rounded-lg shadow-lg mt-1 max-h-40 overflow-y-auto">
                      <div
                        className="px-3 py-2 text-sm text-gray-400 hover:bg-gray-50 cursor-pointer"
                        onClick={() => { setParentSearch(''); setForm({ ...form, parentDepartmentId: '' }) }}
                      >
                        Valideyn yoxdur (ən üst səviyyə)
                      </div>
                      {filteredParents.map(d => (
                        <div
                          key={d.id}
                          className="px-3 py-2 text-sm text-gray-700 hover:bg-purple-50 cursor-pointer"
                          onClick={() => { setParentSearch(d.departmentName); setForm({ ...form, parentDepartmentId: d.id }) }}
                        >
                          {d.departmentName}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </div>

            {/* Footer */}
            <div className="px-6 py-4 border-t border-gray-100 flex items-center justify-end gap-3">
              <button
                onClick={() => setShowModal(false)}
                className="px-5 py-2.5 text-sm border border-gray-200 rounded-lg hover:bg-gray-50 font-medium text-gray-700"
              >
                Ləğv et
              </button>
              <button
                onClick={handleSave}
                disabled={saving}
                className="px-5 py-2.5 text-sm text-white rounded-lg disabled:opacity-50 font-medium"
                style={{ background: '#a855f7' }}
              >
                {saving ? 'Yadda saxlanılır...' : 'Departamenti yadda saxla'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Assign Employees Modal */}
      {assignDept && (
        <div className="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md flex flex-col" style={{ maxHeight: '85vh' }}>
            {/* Header */}
            <div className="px-6 py-5 border-b border-gray-100">
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="text-lg font-bold" style={{ color: '#1e2a4a' }}>{assignDept.departmentName} üçün təyinat</h2>
                  <p className="text-xs text-gray-400 mt-0.5">Bu departamentə təyin etmək üçün əməkdaşları seçin</p>
                </div>
                <button onClick={() => setAssignDept(null)} className="text-gray-400 hover:text-gray-600">
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>
              <div className="relative mt-3">
                <svg className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                </svg>
                <input
                  type="text"
                  value={assignSearch}
                  onChange={e => setAssignSearch(e.target.value)}
                  placeholder="Əməkdaş axtar..."
                  className="w-full border border-gray-200 rounded-lg pl-9 pr-3 py-2 text-sm focus:outline-none focus:ring-2"
                />
              </div>
            </div>

            {/* Employee list */}
            <div className="flex-1 overflow-y-auto px-4 py-3">
              {loadingEmployees ? (
                <div className="flex items-center justify-center py-10 text-gray-400 text-sm">
                  <div className="w-6 h-6 border-2 border-purple-300 border-t-purple-600 rounded-full animate-spin mr-2"></div>
                  Yüklənir...
                </div>
              ) : filteredEmployees.length === 0 ? (
                <div className="text-center py-8 text-gray-400 text-sm">Əməkdaş tapılmadı</div>
              ) : (
                filteredEmployees.map(emp => {
                  const isSelected = selectedEmployeeIds.has(emp.id)
                  const initials = `${emp.firstName?.[0] ?? ''}${emp.lastName?.[0] ?? ''}`.toUpperCase()
                  return (
                    <label
                      key={emp.id}
                      className={`flex items-center gap-3 px-3 py-2.5 rounded-xl cursor-pointer mb-1 transition-colors ${isSelected ? 'bg-purple-50 border border-purple-200' : 'hover:bg-gray-50'}`}
                    >
                      <input
                        type="checkbox"
                        checked={isSelected}
                        onChange={() => toggleEmployee(emp.id)}
                        className="w-4 h-4 rounded accent-purple-600 flex-shrink-0"
                      />
                      <div
                        className="w-9 h-9 rounded-full flex items-center justify-center text-white text-xs font-bold flex-shrink-0"
                        style={{ background: '#a855f7' }}
                      >
                        {initials}
                      </div>
                      <div className="min-w-0">
                        <div className="text-sm font-medium text-gray-800">{emp.firstName} {emp.lastName}</div>
                        <div className="text-xs text-gray-400 truncate">
                          {emp.positionName || 'Vəzifə yoxdur'}{emp.departmentName ? ` • ${emp.departmentName}` : ''}
                        </div>
                      </div>
                    </label>
                  )
                })
              )}
            </div>

            {/* Footer */}
            <div className="px-6 py-4 border-t border-gray-100 flex items-center justify-between">
              <span className="text-sm text-gray-500">{selectedEmployeeIds.size} employee{selectedEmployeeIds.size !== 1 ? 's' : ''} selected</span>
              <div className="flex gap-3">
                <button onClick={() => setAssignDept(null)} className="px-4 py-2 text-sm border border-gray-200 rounded-lg hover:bg-gray-50 font-medium text-gray-700">
                  Ləğv et
                </button>
                <button
                  onClick={handleAssignComplete}
                  disabled={assignSaving}
                  className="px-5 py-2 text-sm text-white rounded-lg disabled:opacity-50 font-medium"
                  style={{ background: '#a855f7' }}
                >
                  {assignSaving ? 'Yadda saxlanılır...' : 'Tamamla'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirmation */}
      {deleteConfirm && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-sm mx-4 p-6">
            <h2 className="text-lg font-bold text-gray-900 mb-2">Departamenti sil</h2>
            <p className="text-gray-600 mb-6 text-sm">
              <strong>{deleteConfirm.departmentName}</strong> departamentini silmək istədiyinizdən əminsiniz?
            </p>
            <div className="flex justify-end gap-3">
              <button onClick={() => setDeleteConfirm(null)} className="px-4 py-2 text-sm border border-gray-300 rounded-lg hover:bg-gray-50">Ləğv et</button>
              <button onClick={handleDelete} className="px-4 py-2 text-sm bg-red-600 text-white rounded-lg hover:bg-red-700">Sil</button>
            </div>
          </div>
        </div>
      )}
    </Layout>
  )
}
