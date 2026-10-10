import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Branch, DeviceConfig } from '../types'
import { deviceApi } from '../api/deviceApi'
import { toast } from '../store/toastStore'
import { getApiErrorMessage } from '../utils/apiError'
import { formatAttendanceDateTime } from '../utils/dateTime'

export default function AreaDevicesPanel({ area, branches, onClose }: { area: Branch; branches: Branch[]; onClose: () => void }) {
  const [devices, setDevices] = useState<DeviceConfig[]>([])
  const [available, setAvailable] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState('')
  const [saving, setSaving] = useState(false)
  const [revision, setRevision] = useState(0)
  const [move, setMove] = useState<{ device: DeviceConfig; areaId: string } | null>(null)
  useEffect(() => {
    let active = true, busy = false
    const load = async () => {
      if (busy) return
      busy = true
      try {
        const { data } = await deviceApi.getHealth()
        if (active) { setDevices(data.data.devices); setAvailable(data.data.available); setError('') }
      } catch (e) { if (active) setError(getApiErrorMessage(e, 'Cihazlar yüklənmədi.')) }
      finally { busy = false; if (active) setLoading(false) }
    }
    void load()
    const timer = window.setInterval(() => { void load() }, 60000)
    return () => { active = false; window.clearInterval(timer) }
  }, [revision])
  const assigned = devices.filter(d => d.branchId === area.id)
  const assign = async (deviceId: number, areaId: number | null) => {
    setSaving(true); setError('')
    try {
      await deviceApi.assignArea(deviceId, areaId)
      setSelected(''); setMove(null); setRevision(n => n + 1)
      toast.success('Cihazın ərazisi yeniləndi', 'Yerli əməkdaş təyinatları yeniləndi. Terminaldakı şəxsləri ayrıca sinxronizasiya edin.')
    } catch (e) { setError(getApiErrorMessage(e, 'Ərazi dəyişdirilmədi.')) }
    finally { setSaving(false) }
  }
  const role = async (device: DeviceConfig, value: string) => {
    setSaving(true); setError('')
    try {
      await deviceApi.assignDoor(device.id, { doorId: device.doorId, role: value || undefined })
      setRevision(n => n + 1); toast.success('Cihazın rolu yeniləndi')
    } catch (e) { setError(getApiErrorMessage(e, 'Rol dəyişdirilmədi.')) }
    finally { setSaving(false) }
  }
  return <div className="fixed inset-0 z-[60] bg-black/40 p-3 sm:p-6 overflow-y-auto" role="dialog" aria-modal="true" aria-label={`${area.name} — cihazlar`}>
    <section className="mx-auto max-w-5xl rounded-xl bg-white p-4 sm:p-6 shadow-xl">
      <div className="flex justify-between gap-4"><div><h2 className="text-xl font-bold">{area.name} — Cihazlar</h2>
        <p className="mt-1 text-sm text-gray-500">{assigned.length} cihaz · {assigned.filter(d => d.doorRole === 'ENTRY').length} giriş · {assigned.filter(d => d.doorRole === 'EXIT').length} çıxış</p></div>
        <button onClick={onClose} disabled={saving} className="border rounded-lg px-3 py-2 self-start">Bağla</button></div>
      {error && <p role="alert" className="mt-4 p-3 rounded bg-red-50 text-red-700">{error}</p>}
      {!loading && !available && <p className="mt-4 p-3 rounded bg-amber-50 text-amber-800">Cihaz xidməti ilə əlaqə yoxdur. Bağlantı vəziyyəti hazırda məlum deyil.</p>}
      <p className="my-4 text-sm text-gray-500">Onlayn vəziyyət bağlantını göstərir. Son sinxronizasiya vaxtını ayrıca yoxlayın.</p>
      <div className="grid gap-3 sm:grid-cols-2">
        {assigned.map(d => <div key={d.id} className="rounded-lg border p-4 space-y-3">
          <div className="flex flex-wrap justify-between gap-2"><strong>{d.deviceName || d.deviceId}</strong><span className={`text-sm rounded px-2 py-1 ${!available || d.status !== 'ACTIVE' ? 'bg-gray-100' : d.online ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'}`}>{d.status !== 'ACTIVE' ? 'Deaktiv' : !available ? 'Məlum deyil' : d.online ? 'Onlayn' : 'Oflayn'}</span></div>
          <p className="text-sm text-gray-600">IP: {d.deviceIp}</p>
          <p className="text-sm text-gray-600">Son sinxronizasiya: {d.lastSyncTime ? formatAttendanceDateTime(d.lastSyncTime) : 'Hələ olmayıb'}</p>
          <label className="block text-sm">Keçid rolu<select aria-label={`${d.deviceName} keçid rolu`} className="ml-2 border rounded p-2" disabled={saving} value={d.doorRole || ''} onChange={e => { void role(d, e.target.value) }}><option value="">Təyin edilməyib</option><option value="ENTRY">Giriş</option><option value="EXIT">Çıxış</option></select></label>
          {!d.doorRole && <p className="text-sm text-amber-700">Davamiyyət üçün giriş və ya çıxış rolu seçin.</p>}
          <div className="flex gap-3 text-sm"><button disabled={saving} onClick={() => setMove({ device: d, areaId: '' })} className="text-violet-700 underline">Ərazini dəyiş</button><Link className="text-violet-700 underline" to={`/devices?device=${d.id}`}>Cihazı idarə et</Link></div>
        </div>)}
      </div>
      {loading ? <p className="py-6">Yüklənir...</p> : !assigned.length && <p className="py-6 text-gray-500">Bu əraziyə cihaz bağlanmayıb.</p>}
      <div className="mt-5 flex flex-wrap gap-2 items-end"><label className="text-sm flex-1 min-w-0">Təyin edilməmiş cihaz<select className="block mt-1 w-full border rounded-lg p-2" value={selected} onChange={e => setSelected(e.target.value)} disabled={saving}><option value="">Cihaz seçin</option>{devices.filter(d => !d.branchId).map(d => <option key={d.id} value={d.id}>{d.deviceName || d.deviceId} — {d.deviceIp}</option>)}</select></label><button disabled={!selected || saving} onClick={() => { void assign(Number(selected), area.id) }} className="rounded-lg bg-violet-600 text-white px-4 py-2 disabled:opacity-50">Əraziyə bağla</button></div>
      <p className="mt-3 text-sm text-gray-500">Yeni cihaz əlavə etmək üçün <Link className="text-violet-700 underline" to={`/devices?area=${area.id}`}>Cihazlar bölməsini açın</Link>.</p>
      {move && <div className="mt-5 rounded-lg border border-amber-200 bg-amber-50 p-4 space-y-3"><strong>{move.device.deviceName} — ərazini dəyiş</strong><p className="text-sm">Ərazi üzrə əməkdaş təyinatları yenilənəcək, fərdi təyinatlar saxlanılacaq. Terminaldakı şəxslər avtomatik dəyişdirilmir. Əvvəlki hesabatların ərazisi saxlanılır.</p><select aria-label="Yeni ərazi" className="w-full border rounded p-2" value={move.areaId} onChange={e => setMove({ ...move, areaId: e.target.value })}><option value="">Ərazidən ayır</option>{branches.filter(b => b.id !== area.id).map(b => <option key={b.id} value={b.id}>{b.name}</option>)}</select><div className="flex gap-2"><button disabled={saving} className="bg-violet-600 text-white rounded px-3 py-2" onClick={() => { void assign(move.device.id, move.areaId ? Number(move.areaId) : null) }}>Dəyişikliyi saxla</button><button disabled={saving} onClick={() => setMove(null)} className="border rounded px-3 py-2">İmtina</button></div></div>}
    </section>
  </div>
}
