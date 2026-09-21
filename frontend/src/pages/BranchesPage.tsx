import { FormEvent, useEffect, useMemo, useState } from 'react'
import Layout from '../components/Layout.tsx'
import { Branch } from '../types'
import { useBranchStore } from '../store/branchStore.ts'

interface BranchFormData {
  name: string
  city: string
  address: string
  status: 'ACTIVE' | 'INACTIVE'
  isHeadOffice: boolean
}

const defaultForm: BranchFormData = {
  name: '',
  city: '',
  address: '',
  status: 'ACTIVE',
  isHeadOffice: false,
}

export default function BranchesPage() {
  const { branches, loading, error, fetchBranches, createBranch, updateBranch, deleteBranch } = useBranchStore()
  const [openModal, setOpenModal] = useState(false)
  const [editingBranch, setEditingBranch] = useState<Branch | null>(null)
  const [form, setForm] = useState<BranchFormData>(defaultForm)
  const [formError, setFormError] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Branch | null>(null)

  useEffect(() => {
    fetchBranches()
  }, [fetchBranches])

  const sortedBranches = useMemo(
    () => [...branches].sort((a, b) => a.name.localeCompare(b.name, 'az')),
    [branches]
  )

  const closeModal = () => {
    setOpenModal(false)
    setEditingBranch(null)
    setForm(defaultForm)
    setFormError(null)
  }

  const openCreate = () => {
    setEditingBranch(null)
    setForm(defaultForm)
    setOpenModal(true)
  }

  const openEdit = (branch: Branch) => {
    setEditingBranch(branch)
    setForm({
      name: branch.name || '',
      city: branch.city || '',
      address: branch.address || '',
      status: branch.status === 'INACTIVE' ? 'INACTIVE' : 'ACTIVE',
      isHeadOffice: !!branch.isHeadOffice,
    })
    setOpenModal(true)
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (!form.name.trim()) {
      setFormError('Filial adı tələb olunur')
      return
    }

    try {
      const payload = {
        name: form.name.trim(),
        city: form.city.trim() || undefined,
        address: form.address.trim() || undefined,
        status: form.status,
        isHeadOffice: form.isHeadOffice,
      }
      if (editingBranch) {
        await updateBranch(editingBranch.id, payload)
      } else {
        await createBranch(payload)
      }
      closeModal()
    } catch (err) {
      setFormError((err as Error).message || 'Filial yadda saxlanılmadı')
    }
  }

  const confirmDelete = async () => {
    if (!deleteTarget) return
    try {
      await deleteBranch(deleteTarget.id)
      setDeleteTarget(null)
    } catch {
      setDeleteTarget(null)
    }
  }

  return (
    <Layout>
      <div className="p-6">
        <div className="flex items-center justify-between mb-6">
          <h1 className="text-2xl font-bold text-gray-900">Filiallar</h1>
          <button
            onClick={openCreate}
            className="px-4 py-2 rounded-lg text-white font-medium"
            style={{ background: '#a855f7' }}
          >
            + Filial əlavə et
          </button>
        </div>

        {error && <div className="mb-4 p-3 rounded-lg bg-red-50 text-red-700 border border-red-200">{error}</div>}

        <div className="bg-white rounded-xl shadow-sm overflow-x-auto border border-gray-200">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-gray-600">
              <tr>
                <th className="px-4 py-3 text-left">Ad</th>
                <th className="px-4 py-3 text-left">Şəhər</th>
                <th className="px-4 py-3 text-left">Ünvan</th>
                <th className="px-4 py-3 text-left">Status</th>
                <th className="px-4 py-3 text-left">Baş ofis</th>
                <th className="px-4 py-3 text-right">Əməliyyat</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td className="px-4 py-6 text-gray-500" colSpan={6}>Yüklənir...</td></tr>
              ) : sortedBranches.length === 0 ? (
                <tr><td className="px-4 py-6 text-gray-500" colSpan={6}>Filial tapılmadı</td></tr>
              ) : (
                sortedBranches.map((branch) => (
                  <tr key={branch.id} className="border-t border-gray-100">
                    <td className="px-4 py-3 font-medium text-gray-900">{branch.name}</td>
                    <td className="px-4 py-3 text-gray-700">{branch.city || '—'}</td>
                    <td className="px-4 py-3 text-gray-700">{branch.address || '—'}</td>
                    <td className="px-4 py-3">
                      <span className={`px-2 py-1 rounded-full text-xs font-semibold ${branch.status === 'INACTIVE' ? 'bg-red-100 text-red-700' : 'bg-emerald-100 text-emerald-700'}`}>
                        {branch.status === 'INACTIVE' ? 'Deaktiv' : 'Aktiv'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-gray-700">{branch.isHeadOffice ? 'Bəli' : 'Xeyr'}</td>
                    <td className="px-4 py-3 text-right space-x-2">
                      <button onClick={() => openEdit(branch)} className="px-3 py-1.5 rounded border border-gray-300 text-gray-700">Redaktə et</button>
                      <button onClick={() => setDeleteTarget(branch)} className="px-3 py-1.5 rounded border border-red-300 text-red-600">Sil</button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {openModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <form onSubmit={handleSubmit} className="w-full max-w-lg bg-white rounded-xl p-6 shadow-lg space-y-4">
            <h2 className="text-xl font-semibold text-gray-900">{editingBranch ? 'Filialı redaktə et' : 'Yeni filial'}</h2>
            {formError && <div className="p-2 rounded bg-red-50 text-red-700 text-sm">{formError}</div>}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Ad</label>
              <input value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} className="w-full border border-gray-300 rounded-lg px-3 py-2" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Şəhər</label>
              <input value={form.city} onChange={(e) => setForm((f) => ({ ...f, city: e.target.value }))} className="w-full border border-gray-300 rounded-lg px-3 py-2" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Ünvan</label>
              <textarea value={form.address} onChange={(e) => setForm((f) => ({ ...f, address: e.target.value }))} className="w-full border border-gray-300 rounded-lg px-3 py-2 min-h-20" />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Status</label>
                <select value={form.status} onChange={(e) => setForm((f) => ({ ...f, status: e.target.value as 'ACTIVE' | 'INACTIVE' }))} className="w-full border border-gray-300 rounded-lg px-3 py-2">
                  <option value="ACTIVE">Aktiv</option>
                  <option value="INACTIVE">Deaktiv</option>
                </select>
              </div>
              <div className="flex items-center gap-2 mt-7">
                <input id="isHeadOffice" type="checkbox" checked={form.isHeadOffice} onChange={(e) => setForm((f) => ({ ...f, isHeadOffice: e.target.checked }))} />
                <label htmlFor="isHeadOffice" className="text-sm text-gray-700">Baş ofis</label>
              </div>
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <button type="button" onClick={closeModal} className="px-4 py-2 border rounded-lg text-gray-700">Bağla</button>
              <button type="submit" className="px-4 py-2 rounded-lg text-white" style={{ background: '#a855f7' }}>Yadda saxla</button>
            </div>
          </form>
        </div>
      )}

      {deleteTarget && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="w-full max-w-md bg-white rounded-xl p-6 space-y-4">
            <h3 className="text-lg font-semibold text-gray-900">Filialı silmək istədiyinizə əminsiniz?</h3>
            <p className="text-sm text-gray-600">{deleteTarget.name}</p>
            <div className="flex justify-end gap-2">
              <button onClick={() => setDeleteTarget(null)} className="px-4 py-2 border rounded-lg text-gray-700">İmtina</button>
              <button onClick={confirmDelete} className="px-4 py-2 rounded-lg bg-red-600 text-white">Sil</button>
            </div>
          </div>
        </div>
      )}
    </Layout>
  )
}
