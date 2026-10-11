import { useEffect, useRef, useState } from 'react'
import { deviceApi, ReconciliationPreview, ReconciliationProgress } from '../api/deviceApi'
import { DeviceConfig } from '../types'
import { getApiErrorMessage } from '../utils/apiError'
import { toast } from '../store/toastStore'

const groups = [
  ['ADD', 'Əlavə olunacaq', 'text-emerald-700 bg-emerald-50'],
  ['REMOVE', 'Cihazdan silinəcək', 'text-red-700 bg-red-50'],
  ['KEEP', 'Saxlanılacaq', 'text-purple-700 bg-purple-50'],
  ['UNKNOWN', 'Tanınmayan', 'text-amber-800 bg-amber-50'],
  ['CONFLICT', 'Yoxlanılmalıdır', 'text-amber-800 bg-amber-50'],
  ['SKIP', 'Əlavə edilməyəcək', 'text-gray-600 bg-gray-100'],
] as const

export default function DeviceReconciliationModal({ device, onClose }: { device: DeviceConfig; onClose: () => void }) {
  const [preview, setPreview] = useState<ReconciliationPreview | null>(null)
  const [progress, setProgress] = useState<ReconciliationProgress | null>(null)
  const [loading, setLoading] = useState(false)
  const [applying, setApplying] = useState(false)
  const [confirmed, setConfirmed] = useState(false)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  const [group, setGroup] = useState<string>('ADD')
  const [query, setQuery] = useState('')
  const busy = useRef(false)
  const dialog = useRef<HTMLElement>(null)

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    dialog.current?.focus()
    return () => previous?.focus()
  }, [])
  useEffect(() => {
    let active = true
    setLoading(true); setPreview(null); setProgress(null); setConfirmed(false); setError('')
    deviceApi.previewReconciliation(device.id).then(({ data }) => {
      if (active) {
        setPreview(data.data)
        setGroup(groups.find(([key]) => data.data.rows.some(r => r.action === key))?.[0] || 'ADD')
      }
    }).catch(e => { if (active) setError(getApiErrorMessage(e, 'Cihazdakı əməkdaşlar oxunmadı.')) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [device.id, revision])

  const apply = async () => {
    if (!preview || !confirmed || busy.current) return
    busy.current = true; setApplying(true); setError('')
    try {
      let step = 0
      while (step < preview.operations) {
        const { data } = await deviceApi.applyReconciliation(device.id, preview.token, step)
        setProgress(data.data)
        if (data.data.finished) {
          const warnings = data.data.outcomes.some(o => o.status !== 'SUCCESS')
          if (warnings) toast.warning('Uyğunlaşdırma dayandırıldı', 'Nəticələri yoxlayıb yenidən önbaxış edin.')
          else toast.success('Əməkdaşlar uyğunlaşdırıldı')
          break
        }
        if (data.data.nextStep <= step) throw new Error('Əməliyyat davam etmədi.')
        step = data.data.nextStep
      }
    } catch (e) {
      setError(getApiErrorMessage(e, 'Əlaqə kəsildi. Son əməliyyat cihazda tətbiq olunmuş ola bilər; yenidən önbaxış edin.'))
      toast.warning('Uyğunlaşdırma tamamlanmadı', 'Nəticəni yenidən önbaxışla yoxlayın.')
    } finally {
      setPreview(null); setConfirmed(false); setApplying(false); busy.current = false
    }
  }
  const secondary = 'px-4 py-2 text-sm font-medium rounded-lg bg-gray-100 text-gray-700 hover:bg-gray-200 disabled:opacity-50'
  const visible = preview?.rows.filter(r => r.action === group && `${r.name} ${r.employeeNo}`.toLocaleLowerCase('az').includes(query.toLocaleLowerCase('az'))) || []
  return (
    <div className="fixed inset-0 z-[70] bg-black/40 flex items-center justify-center p-3 sm:p-6">
      <section ref={dialog} tabIndex={-1} role="dialog" aria-modal="true" aria-labelledby="reconciliation-title"
        className="w-full max-w-3xl max-h-[90dvh] flex flex-col bg-white rounded-xl shadow-xl overflow-hidden outline-none"
        onKeyDown={e => {
          if (e.key === 'Escape' && !applying) onClose()
          if (e.key === 'Tab') {
            const elements = dialog.current?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), [tabindex="0"]')
            const first = elements?.[0], last = elements?.[elements.length - 1]
            if (e.shiftKey && (document.activeElement === first || document.activeElement === dialog.current)) { e.preventDefault(); last?.focus() }
            if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first?.focus() }
          }
        }}>
        <header className="px-5 py-5 border-b border-gray-100 flex justify-between gap-3">
          <div><h2 id="reconciliation-title" className="text-xl font-bold text-gray-900">Əməkdaşları uyğunlaşdır</h2>
            <p className="mt-1 text-sm text-gray-500 break-all">{preview?.deviceName || device.deviceName} · {preview?.deviceIp || device.deviceIp}</p></div>
          <button disabled={applying} onClick={onClose} className={secondary} aria-label="Bağla">✕</button>
        </header>
        <div className="p-5 overflow-y-auto min-h-0 space-y-4 bg-slate-50">
          <p className="text-sm text-gray-600">Cari ərazi və fərdi təyinatlar əsas götürülür. Silinmə yalnız bu cihazdakı əməkdaş qeydini əhatə edir. Proqramdakı profil və davamiyyət tarixçəsi saxlanılır.</p>
          {loading && <p role="status" className="py-8 text-center text-gray-500">Cihazdakı əməkdaşlar yoxlanılır...</p>}
          {error && <p role="alert" className="rounded-lg bg-red-50 border border-red-200 p-3 text-sm text-red-700">{error}</p>}
          {preview && <>
            <div className="flex flex-wrap gap-2" aria-label="Əməliyyat qrupları">
              {groups.map(([key, label, color]) => <button key={key} onClick={() => setGroup(key)} aria-pressed={group === key}
                className={`px-3 py-2 rounded-lg text-xs font-medium ${color} ${group === key ? 'ring-2 ring-purple-500' : ''}`}>
                {label} <strong className="ml-1">{preview.rows.filter(r => r.action === key).length}</strong>
              </button>)}
            </div>
            <input aria-label="Əməkdaş axtar" placeholder="Ad və ya terminal nömrəsi ilə axtar" value={query} onChange={e => setQuery(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:ring-2 focus:ring-purple-500 focus:outline-none" />
            <ul className="divide-y divide-gray-100 rounded-xl bg-white border border-gray-100">
              {visible.map((row, index) => <li key={`${row.employeeNo}-${index}`} className="p-3 text-sm">
                <p className="font-medium text-gray-900 break-words">{row.name || 'Adsız'} <span className="text-gray-400 font-normal">· {row.employeeNo}</span></p>
                <p className="mt-1 text-xs text-gray-500">{row.reason}</p>
              </li>)}
              {!visible.length && <li className="p-6 text-sm text-center text-gray-400">Bu qrupda əməkdaş yoxdur.</li>}
            </ul>
            {!preview.operations && <p className="text-sm text-gray-600">Tətbiq ediləcək dəyişiklik yoxdur.</p>}
            {preview.operations > 0 && <label className="flex items-start gap-3 text-sm text-gray-700 rounded-lg bg-purple-50 p-3">
              <input type="checkbox" className="mt-1 accent-purple-500" checked={confirmed} disabled={applying} onChange={e => setConfirmed(e.target.checked)} />
              <span>Əlavə olunacaq və cihazdan silinəcək əməkdaşları yoxladım, dəyişiklikləri təsdiqləyirəm.</span>
            </label>}
          </>}
          {progress && <div aria-live="polite" className="space-y-2">
            <p className="text-sm font-semibold text-gray-900">İcra olunan addımlar: {progress.nextStep}{preview ? ` / ${preview.operations}` : ''}</p>
            {progress.outcomes.map((o, index) => <div key={index} className={`rounded-lg p-3 text-sm ${o.status === 'SUCCESS' ? 'bg-emerald-50 text-emerald-800' : 'bg-amber-50 text-amber-800'}`}>
              <strong>{o.row.name}</strong> · {o.message}
            </div>)}
          </div>}
        </div>
        <footer className="px-5 py-4 border-t border-gray-100 flex flex-wrap justify-end gap-2">
          <button disabled={applying} onClick={onClose} className={secondary}>Bağla</button>
          <button disabled={applying || loading} onClick={() => setRevision(n => n + 1)} className={secondary}>Yenidən önbaxış</button>
          <button disabled={loading || applying || !preview?.operations || !confirmed} onClick={() => { void apply() }}
            className="px-4 py-2 text-sm font-medium rounded-lg bg-purple-500 text-white hover:bg-purple-600 disabled:opacity-50">
            {applying ? 'Tətbiq olunur...' : 'Dəyişiklikləri tətbiq et'}
          </button>
        </footer>
      </section>
    </div>
  )
}
