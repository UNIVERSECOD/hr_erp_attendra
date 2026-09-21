import { useEffect, useState } from 'react'
import Layout from '../components/Layout.tsx'
import { Position, Department } from '../types'
import { positionApi } from '../api/positionApi.ts'
import { departmentApi } from '../api/departmentApi.ts'
import { useBranchStore } from '../store/branchStore.ts'

interface PositionFormData {
  positionName: string
  description: string
  departmentId: number | ''
}

const defaultForm: PositionFormData = {
  positionName: '',
  description: '',
  departmentId: '',
}

export default function PositionsPage() {
  const [positions, setPositions] = useState<Position[]>([])
  const [departments, setDepartments] = useState<Department[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showModal, setShowModal] = useState(false)
  const [editingPosition, setEditingPosition] = useState<Position | null>(null)
  const [form, setForm] = useState<PositionFormData>(defaultForm)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [deleteConfirm, setDeleteConfirm] = useState<Position | null>(null)
  const [filterDept, setFilterDept] = useState<string>('')
  // UI-only: branch filter for department dropdown in modal
  const [modalBranchId, setModalBranchId] = useState<number | ''>('')
  const { branches, fetchBranches } = useBranchStore()

  const fetchPositions = async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await positionApi.getAll()
      setPositions(res.data?.data ?? [])
    } catch {
      setError('Vəzifələr yüklənərkən xəta baş verdi.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchPositions()
    departmentApi.getAll().then(res => setDepartments(res.data?.data ?? []))
    fetchBranches()
  }, [fetchBranches])

  const openCreate = () => {
    setEditingPosition(null)
    setForm(defaultForm)
    setModalBranchId('')
    setFormError(null)
    setShowModal(true)
  }

  const openEdit = (pos: Position) => {
    setEditingPosition(pos)
    setForm({
      positionName: pos.positionName,
      description: pos.description || '',
      departmentId: pos.departmentId || '',
    })
    // Pre-select branch based on department's branch
    const dept = departments.find(d => d.id === pos.departmentId)
    setModalBranchId(dept?.branchId || '')
    setFormError(null)
    setShowModal(true)
  }

  const handleSave = async () => {
    if (!form.positionName.trim()) {
      setFormError('Vəzifə adı mütləqdir.')
      return
    }
    if (!form.departmentId) {
      setFormError('Departament mütləqdir.')
      return
    }
    setSaving(true)
    setFormError(null)
    try {
      const payload = {
        positionName: form.positionName,
        description: form.description,
        departmentId: Number(form.departmentId),
      }
      if (editingPosition) {
        await positionApi.update(editingPosition.id, payload)
      } else {
        await positionApi.create(payload)
      }
      setShowModal(false)
      await fetchPositions()
    } catch (e: unknown) {
      setFormError((e as Error).message || 'Saxlamaq alınmadı')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    if (!deleteConfirm) return
    try {
      await positionApi.delete(deleteConfirm.id)
      setDeleteConfirm(null)
      await fetchPositions()
    } catch {
      // ignore
    }
  }

  const filtered = filterDept
    ? positions.filter(p => String(p.departmentId) === filterDept)
    : positions

  return (
    <Layout>
      <div className="p-4 sm:p-8" style={{ background: '#f8fafc', minHeight: '100vh' }}>
        {/* Header */}
        <div className="flex flex-wrap items-start justify-between gap-3 mb-6">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Bütün vəzifələr</h1>
            <p className="text-sm text-gray-500 mt-1">
              {positions.length} kateqoriyalaşdırılmış iş vəzifəsi departamentlər üzrə
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={fetchPositions}
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
              + Vəzifə əlavə et
            </button>
          </div>
        </div>

        {/* Filter */}
        <div className="bg-white rounded-xl shadow-sm p-4 mb-4">
          <select value={filterDept} onChange={e => setFilterDept(e.target.value)} className="border border-gray-200 rounded-lg px-3 py-2 text-sm text-gray-600 focus:outline-none focus:ring-1 focus:ring-purple-400">
            <option value="">Bütün departamentlər</option>
            {departments.map(d => <option key={d.id} value={String(d.id)}>{d.departmentName}</option>)}
          </select>
        </div>

        {/* Content */}
        {loading ? (
          <div className="bg-white rounded-xl shadow-sm p-12 text-center text-gray-400">
            <div className="w-8 h-8 border-2 border-purple-300 border-t-purple-600 rounded-full animate-spin mx-auto mb-3"></div>
            Yüklənir...
          </div>
        ) : error ? (
          <div className="bg-white rounded-xl shadow-sm p-8 text-center text-red-500">{error}</div>
        ) : filtered.length === 0 ? (
          <div className="bg-white rounded-xl shadow-sm p-12 text-center text-gray-400">
            <svg className="w-12 h-12 mx-auto mb-3 text-gray-200" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 13.255A23.931 23.931 0 0112 15c-3.183 0-6.22-.62-9-1.745M16 6V4a2 2 0 00-2-2h-4a2 2 0 00-2 2v2m4 6h.01M5 20h14a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
            </svg>
            Hələ heç bir vəzifə yoxdur.
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {filtered.map((pos) => (
              <div key={pos.id} className="bg-white rounded-xl shadow-sm p-5">
                {/* Header */}
                <div className="flex items-start gap-3 mb-4">
                  <div className="w-11 h-11 rounded-xl flex items-center justify-center flex-shrink-0" style={{ background: '#ede9fe' }}>
                    <svg className="w-6 h-6" style={{ color: '#a855f7' }} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 13.255A23.931 23.931 0 0112 15c-3.183 0-6.22-.62-9-1.745M16 6V4a2 2 0 00-2-2h-4a2 2 0 00-2 2v2m4 6h.01M5 20h14a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                    </svg>
                  </div>
                  <div className="flex-1 min-w-0">
                    <h3 className="font-semibold text-gray-900 text-sm">{pos.positionName}</h3>
                    <p className="text-xs text-gray-400 mt-0.5 line-clamp-2">{pos.description || 'Təsvir yoxdur'}</p>
                  </div>
                </div>

                {/* Info rows */}
                <div className="space-y-2 mb-4">
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-gray-400 font-medium">DEPARTAMENT</span>
                    <span className="text-gray-700">{pos.departmentName || '—'}</span>
                  </div>
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-gray-400 font-medium">TƏYİN EDİLMİŞ İŞÇİLƏR</span>
                    <span className="font-semibold text-gray-800">{pos.employeeCount ?? 0}</span>
                  </div>
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-gray-400 font-medium">QAYDALAR</span>
                    <div className="flex gap-1">
                      <span className="px-2 py-0.5 rounded-full text-xs font-medium" style={{ background: '#fef3c7', color: '#92400e' }}>Əlavə iş deaktiv</span>
                      <span className="px-2 py-0.5 rounded-full text-xs font-medium" style={{ background: '#ede9fe', color: '#6d28d9' }}>Standart Növbə</span>
                    </div>
                  </div>
                </div>

                {/* Actions */}
                <div className="flex items-center gap-2 pt-3 border-t border-gray-100">
                  <button
                    onClick={() => openEdit(pos)}
                    className="flex-1 flex items-center justify-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border transition-colors"
                    style={{ color: '#a855f7', borderColor: '#e9d5ff', background: '#faf5ff' }}
                  >
                    <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                    </svg>
                    Redaktə et
                  </button>
                  <button
                    onClick={() => setDeleteConfirm(pos)}
                    className="flex-1 flex items-center justify-center gap-1.5 px-3 py-1.5 text-xs font-medium text-red-600 bg-red-50 rounded-lg border border-red-100 hover:bg-red-100 transition-colors"
                  >
                    <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                    </svg>
                    Sil
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Create/Edit Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-lg p-6">
            <h2 className="text-xl font-bold text-gray-900 mb-4">
              {editingPosition ? 'Vəzifəni redaktə et' : 'Vəzifə əlavə et'}
            </h2>
            {formError && <div className="bg-red-50 border border-red-200 text-red-700 px-3 py-2 rounded-lg mb-4 text-sm">{formError}</div>}
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Filial</label>
                <select
                  value={modalBranchId}
                  onChange={(e) => {
                    setModalBranchId(e.target.value ? Number(e.target.value) : '')
                    setForm({ ...form, departmentId: '' })
                  }}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                >
                  <option value="">Bütün filiallar</option>
                  {branches.map(b => <option key={b.id} value={b.id}>{b.name}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Vəzifə adı *</label>
                <input
                  type="text"
                  value={form.positionName}
                  onChange={(e) => setForm({ ...form, positionName: e.target.value })}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Təsvir</label>
                <textarea
                  value={form.description}
                  onChange={(e) => setForm({ ...form, description: e.target.value })}
                  rows={3}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Departament *</label>
                <select
                  value={form.departmentId}
                  onChange={(e) => setForm({ ...form, departmentId: e.target.value ? Number(e.target.value) : '' })}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                >
                  <option value="">Seçin...</option>
                  {departments
                    .filter(d => !modalBranchId || d.branchId === Number(modalBranchId))
                    .map(d => <option key={d.id} value={d.id}>{d.departmentName}</option>)}
                </select>
              </div>
            </div>
            <div className="flex justify-end gap-3 mt-6">
              <button onClick={() => setShowModal(false)} className="px-4 py-2 text-sm border border-gray-300 rounded-lg hover:bg-gray-50">Ləğv et</button>
              <button onClick={handleSave} disabled={saving} className="px-4 py-2 text-sm text-white rounded-lg disabled:opacity-50" style={{ background: '#a855f7' }}>
                {saving ? 'Saxlanılır...' : editingPosition ? 'Yenilə' : 'Yarat'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirmation */}
      {deleteConfirm && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-sm mx-4 p-6">
            <h2 className="text-lg font-bold text-gray-900 mb-2">Vəzifəni sil</h2>
            <p className="text-gray-600 mb-6 text-sm">
              <strong>{deleteConfirm.positionName}</strong> vəzifəsini silmək istədiyinizdən əminsiniz?
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
