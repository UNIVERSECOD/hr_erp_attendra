import { useEffect, useRef, useState } from 'react'
import Layout from '../components/Layout.tsx'
import { useDeviceStore } from '../store/deviceStore.ts'
import { DeviceConfig, Branch, Door } from '../types'
import { branchApi } from '../api/branchApi.ts'
import { doorApi } from '../api/doorApi.ts'
import { deviceApi } from '../api/deviceApi.ts'
import { t } from '../i18n/index.ts'
import { doorRoleLabel, statusLabel } from '../i18n/labels.ts'
import { isDeviceOnline, relativeTime, ONLINE_THRESHOLD_MINUTES } from '../utils/deviceOnline.ts'

interface DeviceFormData {
  deviceName: string
  deviceIp: string
  devicePort: number | ''
  username: string
  password: string
  branchId: number | ''
  status: string
}

const defaultForm: DeviceFormData = {
  deviceName: '',
  deviceIp: '',
  devicePort: 80,
  username: 'admin',
  password: '',
  branchId: '',
  status: 'ACTIVE',
}

export default function DevicesPage() {
  const { devices, loading, error, fetchDevices, syncDevice, createDevice, updateDevice, deleteDevice } = useDeviceStore()
  const [branches, setBranches] = useState<Branch[]>([])
  const [showModal, setShowModal] = useState(false)
  const [editingDevice, setEditingDevice] = useState<DeviceConfig | null>(null)
  const [form, setForm] = useState<DeviceFormData>(defaultForm)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [deleteConfirm, setDeleteConfirm] = useState<DeviceConfig | null>(null)
  const [syncingId, setSyncingId] = useState<number | null>(null)
  const [syncFeedback, setSyncFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null)
  const [doors, setDoors] = useState<Door[]>([])
  const [selectedDoorId, setSelectedDoorId] = useState<number | '' | 'new'>('')
  const [doorRole, setDoorRole] = useState<'ENTRY' | 'EXIT' | ''>('')
  const [newDoorName, setNewDoorName] = useState('')
  const [showDoorManager, setShowDoorManager] = useState(false)
  const [managerBranchId, setManagerBranchId] = useState<number | ''>('')
  const [managerDoors, setManagerDoors] = useState<Door[]>([])
  const [managerLoading, setManagerLoading] = useState(false)
  const [doorDeleteConfirm, setDoorDeleteConfirm] = useState<Door | null>(null)
  // Live clock tick — re-renders every 30 s so online/offline badge updates automatically
  const [, setTick] = useState(0)
  const tickRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    fetchDevices()
    branchApi.getAll().then((res) => setBranches(res.data?.data ?? []))
    // Refresh online badges every 30 seconds without a network call
    tickRef.current = setInterval(() => setTick((t) => t + 1), 30_000)
    return () => { if (tickRef.current) clearInterval(tickRef.current) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const loadDoors = async (branchId: number) => {
    try {
      const res = await doorApi.getByBranch(branchId)
      setDoors(res.data?.data ?? [])
    } catch {
      setDoors([])
    }
  }

  const loadManagerDoors = async (branchId: number) => {
    setManagerLoading(true)
    try {
      const res = await doorApi.getByBranch(branchId)
      setManagerDoors(res.data?.data ?? [])
    } catch {
      setManagerDoors([])
    } finally {
      setManagerLoading(false)
    }
  }

  const handleDeleteDoor = async () => {
    if (!doorDeleteConfirm) return
    try {
      await doorApi.delete(doorDeleteConfirm.id)
      setDoorDeleteConfirm(null)
      if (managerBranchId) {
        await loadManagerDoors(Number(managerBranchId))
      }
      // Also refresh device list since devices may have been unassigned
      await fetchDevices()
    } catch (e: any) {
      alert(e?.response?.data?.message || 'Qapını silmək alınmadı')
    }
  }

  const openCreate = () => {
    setEditingDevice(null)
    setForm(defaultForm)
    setSelectedDoorId('')
    setDoorRole('')
    setNewDoorName('')
    setDoors([])
    setFormError(null)
    setShowModal(true)
  }

  const openEdit = (device: DeviceConfig) => {
    setEditingDevice(device)
    setForm({
      deviceName: device.deviceName || '',
      deviceIp: device.deviceIp,
      devicePort: device.devicePort || 80,
      username: device.username || 'admin',
      password: '',
      branchId: device.branchId || '',
      status: device.status || 'ACTIVE',
    })
    setSelectedDoorId(device.doorId ?? '')
    setDoorRole((device.doorRole as 'ENTRY' | 'EXIT') ?? '')
    setNewDoorName('')
    setFormError(null)
    if (device.branchId) {
      loadDoors(device.branchId)
    }
    setShowModal(true)
  }

  const handleSave = async () => {
    if (!form.deviceIp.trim()) { setFormError('Cihaz IP-si tələb olunur.'); return }
    if (!form.username.trim()) { setFormError('İstifadəçi adı tələb olunur.'); return }
    if (!editingDevice && !form.password.trim()) { setFormError('Şifrə tələb olunur.'); return }
    if (form.branchId && selectedDoorId === 'new' && !newDoorName.trim()) {
      setFormError('Yeni qapı yaradarkən qapı adı tələb olunur.')
      return
    }
    setSaving(true)
    setFormError(null)
    try {
      const payload = {
        ...form,
        devicePort: form.devicePort ? Number(form.devicePort) : 80,
        branchId: form.branchId ? Number(form.branchId) : undefined,
      }
      let deviceId = editingDevice?.id
      if (editingDevice) {
        await updateDevice(editingDevice.id, payload)
        deviceId = editingDevice.id
      } else {
        const createRes = await deviceApi.create(payload)
        const created = Array.isArray(createRes.data) ? createRes.data[0] : createRes.data?.data ?? createRes.data
        deviceId = typeof created?.id === 'number' ? created.id : undefined
      }

      // Door and Role assignment
      if (deviceId && form.branchId) {
        let doorId = selectedDoorId && selectedDoorId !== 'new' ? Number(selectedDoorId) : undefined;
        if (selectedDoorId === 'new') {
          const createDoorRes = await doorApi.create({
            branchId: Number(form.branchId),
            name: newDoorName.trim(),
            status: 'ACTIVE',
          });
          doorId = createDoorRes.data?.data?.id;
        }
        await deviceApi.assignDoor(deviceId, {
          doorId: doorId || undefined,
          role: doorRole || undefined
        });
      }

      await fetchDevices()
      setShowModal(false)
    } catch (e: unknown) {
      setFormError((e as Error).message || 'Cihazı yadda saxlamaq alınmadı')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    if (!deleteConfirm) return
    try {
      await deleteDevice(deleteConfirm.id)
      setDeleteConfirm(null)
    } catch {
      // handled by store
    }
  }

  const handleSync = async (id: number) => {
    setSyncingId(id)
    setSyncFeedback(null)
    try {
      await syncDevice(id)
      setSyncFeedback({ type: 'success', message: 'Cihaz və davamiyyət məlumatları sinxronlaşdırıldı.' })
    } catch (error: unknown) {
      const responseMessage = (error as { response?: { data?: { message?: string } } })?.response?.data?.message
      setSyncFeedback({ type: 'error', message: responseMessage || 'Cihazı sinxronlaşdırmaq alınmadı.' })
    } finally {
      setSyncingId(null)
    }
  }

  // Compute online/offline counts using lastSyncTime-based real-time logic
  const activeCount = devices.filter((d: DeviceConfig) => isDeviceOnline(d.status, d.lastSyncTime)).length
  const inactiveCount = devices.length - activeCount

  return (
    <Layout>
      <div className="p-4 sm:p-8" style={{ background: '#f8fafc', minHeight: '100vh' }}>
        {/* Header */}
        <div className="flex flex-wrap items-start justify-between gap-3 mb-6">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Cihazlar</h1>
            <p className="text-sm text-gray-500 mt-1">
              <span className="inline-flex items-center gap-1.5 mr-3">
                <span className="w-2 h-2 rounded-full bg-green-500 inline-block"></span>
                {activeCount} onlayn
              </span>
              <span className="inline-flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-red-400 inline-block"></span>
                {inactiveCount} oflayn
              </span>
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => fetchDevices()}
              className="flex items-center gap-1.5 px-3 py-2 text-sm font-medium text-gray-600 bg-white border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
              Siyahını yenilə
            </button>
            <button
              onClick={() => { setShowDoorManager(true); setManagerBranchId(''); setManagerDoors([]) }}
              className="flex items-center gap-1.5 px-3 py-2 text-sm font-medium text-gray-600 bg-white border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
              </svg>
              Qapıları idarə et
            </button>
            <button
              onClick={openCreate}
              className="flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-white rounded-lg transition-colors"
              style={{ background: '#a855f7' }}
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
              </svg>
              Cihaz əlavə et
            </button>
          </div>
        </div>

        {syncFeedback && (
          <div
            className={`mb-4 border px-4 py-3 text-sm rounded-lg ${
              syncFeedback.type === 'success'
                ? 'border-green-200 bg-green-50 text-green-700'
                : 'border-red-200 bg-red-50 text-red-700'
            }`}
            role="status"
          >
            {syncFeedback.message}
          </div>
        )}

        {/* Summary cards */}
        <div className="grid grid-cols-3 gap-4 mb-6">
          <div className="bg-white rounded-xl p-4 shadow-sm flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg flex items-center justify-center" style={{ background: '#f3e8ff' }}>
              <svg className="w-5 h-5" style={{ color: '#a855f7' }} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
              </svg>
            </div>
            <div>
              <p className="text-xs text-gray-400">Ümumi cihazlar</p>
              <p className="text-lg font-bold text-gray-900">{devices.length}</p>
            </div>
          </div>
          <div className="bg-white rounded-xl p-4 shadow-sm flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg flex items-center justify-center bg-green-50">
              <span className="w-3 h-3 rounded-full bg-green-500 inline-block"></span>
            </div>
            <div>
              <p className="text-xs text-gray-400">Onlayn</p>
              <p className="text-lg font-bold text-gray-900">{activeCount}</p>
            </div>
          </div>
          <div className="bg-white rounded-xl p-4 shadow-sm flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg flex items-center justify-center bg-red-50">
              <span className="w-3 h-3 rounded-full bg-red-400 inline-block"></span>
            </div>
            <div>
              <p className="text-xs text-gray-400">Oflayn</p>
              <p className="text-lg font-bold text-gray-900">{inactiveCount}</p>
            </div>
          </div>
        </div>

        {/* Device List */}
        {loading ? (
          <div className="bg-white rounded-xl shadow-sm p-12 text-center text-gray-400">
            <div className="w-8 h-8 border-2 border-purple-300 border-t-purple-600 rounded-full animate-spin mx-auto mb-3"></div>
            Cihazlar yüklənir...
          </div>
        ) : error ? (
          <div className="bg-white rounded-xl shadow-sm p-8 text-center text-red-500">{error}</div>
        ) : devices.length === 0 ? (
          <div className="bg-white rounded-xl shadow-sm p-12 text-center">
            <div className="w-16 h-16 rounded-full bg-purple-50 flex items-center justify-center mx-auto mb-4">
              <svg className="w-8 h-8 text-purple-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
              </svg>
            </div>
            <p className="text-gray-500 text-sm">Hələ heç bir cihaz qurulmayıb.</p>
            <button onClick={openCreate} className="mt-3 text-sm font-medium" style={{ color: '#a855f7' }}>
              + İlk cihazı əlavə et
            </button>
          </div>
        ) : (
          <div className="space-y-3">
            {devices.map((device: DeviceConfig) => (
              <div key={device.id} className="bg-white rounded-xl shadow-sm p-5 flex items-center gap-5">
                {/* Icon */}
                <div className="w-12 h-12 rounded-xl flex items-center justify-center flex-shrink-0" style={{ background: '#f3e8ff' }}>
                  <svg className="w-6 h-6" style={{ color: '#a855f7' }} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                  </svg>
                </div>

                {/* Info */}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-0.5">
                    <p className="font-semibold text-gray-900 text-sm">{device.deviceName || device.deviceId}</p>
                    {(() => {
                      const online = isDeviceOnline(device.status, device.lastSyncTime)
                      return (
                        <span
                          className="px-2 py-0.5 rounded-full text-xs font-medium flex items-center gap-1"
                          style={online
                            ? { background: '#d1fae5', color: '#065f46' }
                            : { background: '#fee2e2', color: '#991b1b' }}
                          title={online
                            ? `Onlayn — son ${ONLINE_THRESHOLD_MINUTES} dəqiqə ərzində sinxronlaşdı`
                            : `Oflayn — son sinxron: ${relativeTime(device.lastSyncTime)}`}
                        >
                          {online ? (
                            <span className="relative flex h-2 w-2">
                              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
                              <span className="relative inline-flex rounded-full h-2 w-2 bg-green-500"></span>
                            </span>
                          ) : (
                            <span className="inline-block w-2 h-2 rounded-full bg-red-400"></span>
                          )}
                          {online ? 'Onlayn' : 'Oflayn'}
                        </span>
                      )
                    })()}
                    {device.doorRole && (
                      <span
                        className="px-2 py-0.5 rounded-full text-xs font-medium"
                        style={{ background: '#e0e7ff', color: '#3730a3' }}
                      >
                        {doorRoleLabel(device.doorRole)}
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-gray-400 font-mono">{device.deviceId}</p>
                </div>

                {/* IP */}
                <div className="hidden md:block text-center min-w-[130px]">
                  <p className="text-xs text-gray-400 mb-0.5">IP ünvanı</p>
                  <p className="text-sm font-mono text-gray-700">
                    {device.deviceIp}{device.devicePort && device.devicePort !== 80 ? `:${device.devicePort}` : ''}
                  </p>
                </div>

                {/* Son sinxron — relative + absolute time */}
                <div className="hidden lg:block text-center min-w-[160px]">
                  <p className="text-xs text-gray-400 mb-0.5">Son sinxron</p>
                  {device.lastSyncTime ? (
                    <>
                      <p className="text-sm font-medium text-gray-700">{relativeTime(device.lastSyncTime)}</p>
                      <p className="text-xs text-gray-400">{new Date(device.lastSyncTime).toLocaleString()}</p>
                    </>
                  ) : (
                    <p className="text-sm text-gray-400">Heç vaxt</p>
                  )}
                </div>

                {/* Actions */}
                <div className="flex items-center gap-2 flex-shrink-0">
                  <button
                    onClick={() => handleSync(device.id)}
                    disabled={syncingId === device.id}
                    className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium rounded-lg border transition-colors disabled:opacity-50"
                    style={{ color: '#a855f7', borderColor: '#e9d5ff', background: '#faf5ff' }}
                  >
                    <svg className={`w-3.5 h-3.5 ${syncingId === device.id ? 'animate-spin' : ''}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                    </svg>
                    {syncingId === device.id ? 'Sinxronlaşdırılır...' : 'Sinxron'}
                  </button>
                  <button
                    onClick={() => openEdit(device)}
                    className="px-3 py-1.5 text-xs font-medium text-gray-600 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors"
                  >
                    Redaktə et
                  </button>
                  <button
                    onClick={() => setDeleteConfirm(device)}
                    className="px-3 py-1.5 text-xs font-medium text-red-600 bg-red-50 rounded-lg hover:bg-red-100 transition-colors"
                  >
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
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-lg mx-4 p-6">
            <h2 className="text-xl font-bold text-gray-900 mb-4">
              {editingDevice ? 'Cihazı redaktə et' : 'Cihaz əlavə et'}
            </h2>
            {formError && (
              <div className="bg-red-50 border border-red-200 text-red-700 px-3 py-2 rounded-lg mb-4 text-sm">
                {formError}
              </div>
            )}
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Cihaz adı</label>
                <input
                  type="text"
                  value={form.deviceName}
                  onChange={(e) => setForm({ ...form, deviceName: e.target.value })}
                  placeholder="məs., Əsas giriş"
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">IP ünvanı *</label>
                <input
                  type="text"
                  value={form.deviceIp}
                  onChange={(e) => setForm({ ...form, deviceIp: e.target.value })}
                  placeholder="192.168.1.100"
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Port</label>
                <input
                  type="number"
                  value={form.devicePort}
                  onChange={(e) => setForm({ ...form, devicePort: e.target.value ? Number(e.target.value) : '' })}
                  placeholder="80"
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">İstifadəçi adı</label>
                <input
                  type="text"
                  value={form.username}
                  onChange={(e) => setForm({ ...form, username: e.target.value })}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Şifrə {editingDevice ? '(boş saxlayın ki, dəyişməsin)' : ''}
                </label>
                <input
                  type="password"
                  value={form.password}
                  onChange={(e) => setForm({ ...form, password: e.target.value })}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Filial</label>
                <select
                  value={form.branchId}
                  onChange={(e) => {
                    const branchId = e.target.value ? Number(e.target.value) : ''
                    setForm({ ...form, branchId })
                    setSelectedDoorId('')
                    setDoorRole('')
                    if (branchId) {
                      loadDoors(branchId)
                    } else {
                      setDoors([])
                    }
                  }}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                >
                  <option value="">Filial seçin...</option>
                  {branches.map((b) => (
                    <option key={b.id} value={b.id}>{b.name}</option>
                  ))}
                </select>
              </div>
              {form.branchId && (
                <>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Qapı</label>
                    <select
                      value={selectedDoorId}
                      onChange={(e) => {
                        const val = e.target.value
                        setSelectedDoorId(val === 'new' ? 'new' : val ? Number(val) : '')
                      }}
                      className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                    >
                      <option value="">Yoxdur</option>
                      {doors.map((d) => (
                        <option key={d.id} value={d.id}>{d.name}</option>
                      ))}
                      <option value="new">+ Yeni qapı yarat...</option>
                    </select>
                  </div>
                  {selectedDoorId === 'new' && (
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">Yeni qapı adı</label>
                      <input
                        type="text"
                        value={newDoorName}
                        onChange={(e) => setNewDoorName(e.target.value)}
                        placeholder="məs., Əsas qapı"
                        className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                      />
                    </div>
                  )}
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Rol</label>
                    <select
                      value={doorRole}
                      onChange={(e) => setDoorRole(e.target.value as 'ENTRY' | 'EXIT' | '')}
                      className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                    >
                      <option value="">Rol seçin...</option>
                      <option value="ENTRY">Giriş</option>
                      <option value="EXIT">Çıxış</option>
                    </select>
                  </div>
                </>
              )}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('common.status')}</label>
                <select
                  value={form.status}
                  onChange={(e) => setForm({ ...form, status: e.target.value })}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
                >
                  <option value="ACTIVE">{statusLabel('ACTIVE')}</option>
                  <option value="INACTIVE">{statusLabel('INACTIVE')}</option>
                </select>
              </div>
            </div>
            <div className="flex justify-end gap-3 mt-6">
              <button
                onClick={() => setShowModal(false)}
                className="px-4 py-2 text-sm border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                Ləğv et
              </button>
              <button
                onClick={handleSave}
                disabled={saving}
                className="px-4 py-2 text-sm text-white rounded-lg disabled:opacity-50 transition-colors"
                style={{ background: '#a855f7' }}
              >
                {saving ? 'Yadda saxlanılır...' : editingDevice ? 'Yenilə' : 'Yarat'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Door Manager Modal */}
      {showDoorManager && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-md mx-4 p-6">
            <h2 className="text-xl font-bold text-gray-900 mb-4">Qapıları idarə et</h2>
            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-1">Filial</label>
              <select
                value={managerBranchId}
                onChange={(e) => {
                  const val = e.target.value ? Number(e.target.value) : ''
                  setManagerBranchId(val)
                  if (val) loadManagerDoors(Number(val))
                  else setManagerDoors([])
                }}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm"
              >
                <option value="">Filial seçin...</option>
                {branches.map((b) => (
                  <option key={b.id} value={b.id}>{b.name}</option>
                ))}
              </select>
            </div>
            <div className="max-h-64 overflow-y-auto space-y-2">
              {managerLoading ? (
                <div className="text-center text-gray-400 text-sm py-4">Yüklənir...</div>
              ) : managerDoors.length === 0 ? (
                <div className="text-center text-gray-400 text-sm py-4">
                  {managerBranchId ? 'Bu filial üçün qapı tapılmadı.' : 'Qapıları görmək üçün filial seçin.'}
                </div>
              ) : (
                managerDoors.map((door) => (
                  <div key={door.id} className="flex items-center justify-between bg-gray-50 rounded-lg px-3 py-2">
                    <div>
                      <p className="text-sm font-medium text-gray-800">{door.name}</p>
                      <p className="text-xs text-gray-400">{statusLabel(door.status)}</p>
                    </div>
                    <button
                      onClick={() => setDoorDeleteConfirm(door)}
                      className="px-2 py-1 text-xs font-medium text-red-600 bg-red-50 rounded hover:bg-red-100 transition-colors"
                    >
                      Sil
                    </button>
                  </div>
                ))
              )}
            </div>
            <div className="flex justify-end mt-5">
              <button
                onClick={() => setShowDoorManager(false)}
                className="px-4 py-2 text-sm border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                Bağla
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Door Delete Confirmation */}
      {doorDeleteConfirm && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-sm mx-4 p-6">
            <h2 className="text-lg font-bold text-gray-900 mb-2">{t('devices.deleteDoorTitle')}</h2>
            <p className="text-gray-600 mb-6 text-sm">
              {t('devices.deleteDoorConfirm')}
              {' '}
              <strong>{doorDeleteConfirm.name}</strong>
            </p>
            <div className="flex justify-end gap-3">
              <button
                onClick={() => setDoorDeleteConfirm(null)}
                className="px-4 py-2 text-sm border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                Ləğv et
              </button>
              <button
                onClick={handleDeleteDoor}
                className="px-4 py-2 text-sm bg-red-600 text-white rounded-lg hover:bg-red-700"
              >
                Sil
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Device Delete Confirmation */}
      {deleteConfirm && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-sm mx-4 p-6">
            <h2 className="text-lg font-bold text-gray-900 mb-2">{t('devices.deleteDeviceTitle')}</h2>
            <p className="text-gray-600 mb-6 text-sm">
              {t('devices.deleteDeviceConfirm')}
              {' '}
              <strong>{deleteConfirm.deviceName || deleteConfirm.deviceId}</strong>
            </p>
            <div className="flex justify-end gap-3">
              <button
                onClick={() => setDeleteConfirm(null)}
                className="px-4 py-2 text-sm border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                Ləğv et
              </button>
              <button
                onClick={handleDelete}
                className="px-4 py-2 text-sm bg-red-600 text-white rounded-lg hover:bg-red-700"
              >
                Sil
              </button>
            </div>
          </div>
        </div>
      )}
    </Layout>
  )
}
