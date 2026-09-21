import { useEffect, useState } from 'react'
import Layout from '../components/Layout.tsx'
import { useScheduleStore } from '../store/scheduleStore.ts'
import { Timetable, Holiday, Permission, PermissionType } from '../types'
import ShiftAssignmentPage from './ShiftAssignmentPage.tsx'
import { statusLabel } from '../i18n/labels.ts'

// ─── Shared helpers ───────────────────────────────────────────────────────────
/** UI-exposed shift types only. MORNING/NIGHT remain valid in DB/backend. */
const SHIFT_TYPES = ['STANDARD', 'FLEXIBLE']
const LATE_OPTIONS = [0, 5, 10, 15, 20, 30, 45, 60]
const EARLY_OPTIONS = [0, 5, 10, 15, 30]
const APPLY_SCOPES = ['ALL', 'SPECIFIC_DEPARTMENT', 'SPECIFIC_BRANCH']
const APPLY_SCOPE_LABELS: Record<string, string> = {
  ALL: 'Bütün şirkət',
  SPECIFIC_DEPARTMENT: 'Departament',
  SPECIFIC_BRANCH: 'Filial',
}
const APPLY_TYPES = ['EMPLOYEE', 'DEPARTMENT', 'BRANCH', 'GROUP']
const APPLY_TYPE_LABELS: Record<string, string> = {
  EMPLOYEE: 'Əməkdaş',
  DEPARTMENT: 'Departament',
  BRANCH: 'Filial',
  GROUP: 'Qrup',
}
const PERMISSION_STATUSES = ['ACTIVE', 'INACTIVE', 'PENDING', 'APPROVED', 'REJECTED']
const DEFAULT_TIMETABLE_NAMES = [
  'Standart ofis',
  'Əməliyyat növbəsi',
  'Dəstək növbəsi',
  'Səhər növbəsi',
  'Axşam növbəsi',
]
const DEFAULT_HOLIDAY_NAMES = [
  'Novruz Bayramı',
  'İstehsalat Günü',
  'Məzənnə Günü',
  'Qurban Bayramı',
  'Ramazan Bayramı',
  'Müstəqillik Günü',
  'Respublika Günü',
  'Xüsusi',
]
const DEFAULT_PERMISSION_TYPES: PermissionType[] = [
  { id: 0, code: 'MEDICAL_LEAVE', name: 'Tibbi məzuniyyət' },
  { id: 0, code: 'PREGNANCY_LEAVE', name: 'Hamiləlik məzuniyyəti' },
  { id: 0, code: 'MATERNITY_LEAVE', name: 'Analıq məzuniyyəti' },
  { id: 0, code: 'PARENTAL_LEAVE', name: 'Valideyn məzuniyyəti' },
  { id: 0, code: 'REMOTE_WORK', name: 'Uzaqdan iş' },
  { id: 0, code: 'FLEXIBLE_HOURS', name: 'Sərbəst saatlar' },
  { id: 0, code: 'UNPAID_LEAVE', name: 'Ödənişsiz məzuniyyət' },
  { id: 0, code: 'SABBATICAL', name: 'Uzunmüddətli məzuniyyət' },
]

const LEAVE_TYPE_LABELS: Record<string, string> = {
  MEDICAL_LEAVE: 'Tibbi məzuniyyət',
  PREGNANCY_LEAVE: 'Hamiləlik məzuniyyəti',
  MATERNITY_LEAVE: 'Analıq məzuniyyəti',
  PARENTAL_LEAVE: 'Valideyn məzuniyyəti',
  REMOTE_WORK: 'Uzaqdan iş',
  FLEXIBLE_HOURS: 'Sərbəst saatlar',
  UNPAID_LEAVE: 'Ödənişsiz məzuniyyət',
  SABBATICAL: 'Uzunmüddətli məzuniyyət',
}

const SCOPE_COLORS: Record<string, string> = {
  ALL: 'bg-orange-100 text-orange-700',
  SPECIFIC_DEPARTMENT: 'bg-blue-100 text-blue-700',
  SPECIFIC_BRANCH: 'bg-green-100 text-green-700',
}

function ConfirmDialog({ message, onConfirm, onCancel }: { message: string; onConfirm: () => void; onCancel: () => void }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-2xl shadow-xl p-6 w-full max-w-sm">
        <p className="text-gray-700 mb-6">{message}</p>
        <div className="flex gap-3 justify-end">
          <button onClick={onCancel} className="px-4 py-2 rounded-lg border text-gray-600 hover:bg-gray-50">Ləğv et</button>
          <button onClick={onConfirm} className="px-4 py-2 rounded-lg bg-red-600 text-white hover:bg-red-700">Sil</button>
        </div>
      </div>
    </div>
  )
}

// ─── Tab 1: Timetables ────────────────────────────────────────────────────────
const SHIFT_TYPE_LABELS: Record<string, string> = {
  STANDARD: 'Standart Növbə',
  FLEXIBLE: 'Sərbəst Növbə',
  // Legacy values still stored in DB — shown when editing existing schedules
  FIRST_ENTRY: 'Sərbəst Növbə',
  SERBEST: 'Sərbəst Növbə',
  FREE_SHIFT: 'Sərbəst Növbə',
  FREE: 'Sərbəst Növbə',
  MORNING: 'Standart Növbə',
  NIGHT: 'Standart Növbə',
}

const defaultTimetable = (): Partial<Timetable> => ({
  name: '',
  description: '',
  startTime: '09:00',
  endTime: '18:00',
  breakMinutes: 0,
  allowedLateMinutes: 10,
  allowedEarlyLeaveMinutes: 5,
  shiftType: '',
})

function TimetableModal({ initial, onSave, onClose }: {
  initial?: Partial<Timetable>
  onSave: (data: Partial<Timetable>) => Promise<void>
  onClose: () => void
}) {
  const normalizeUiShiftType = (raw?: string): string => {
    const current = (raw ?? '').toUpperCase()
    if (['FLEXIBLE', 'FIRST_ENTRY', 'SERBEST', 'FREE_SHIFT', 'FREE'].includes(current)) return 'FLEXIBLE'
    if (current) return 'STANDARD'
    return ''
  }

  const [form, setForm] = useState<Partial<Timetable>>(() => {
    const base = initial ?? defaultTimetable()
    return { ...base, shiftType: normalizeUiShiftType(base.shiftType) || base.shiftType || '' }
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const set = (k: keyof Timetable, v: unknown) => setForm(f => ({ ...f, [k]: v }))
  const isFlexible = (form.shiftType ?? '').toUpperCase() === 'FLEXIBLE'
  const shiftTypeOptions = SHIFT_TYPES

  const handleSave = async () => {
    if (!form.name?.trim()) { setError('Növbə adı daxil edilməlidir'); return }
    if (!form.shiftType) { setError('Növbə növü seçilməlidir'); return }
    if (!isFlexible && (!form.startTime || !form.endTime)) {
      setError('Başlanğıc və bitmə vaxtı seçilməlidir')
      return
    }
    setSaving(true)
    try {
      const payload: Partial<Timetable> = { ...form }
      if (isFlexible) {
        // Sərbəst Növbə has no fixed hours / late grace — placeholders for NOT NULL DB columns.
        payload.startTime = '00:00'
        payload.endTime = '23:59'
        payload.allowedLateMinutes = 0
        payload.allowedEarlyLeaveMinutes = 0
        payload.shiftType = 'FLEXIBLE'
      } else {
        payload.shiftType = 'STANDARD'
      }
      await onSave(payload)
      onClose()
    }
    catch { setError('Xəta baş verdi') }
    finally { setSaving(false) }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-2xl shadow-xl p-6 w-full max-w-md">
        <h2 className="text-lg font-bold text-gray-900 mb-4">{initial?.id ? 'İş qrafikini redaktə et' : 'Yeni iş qrafiki'}</h2>
        {error && <p className="text-red-500 text-sm mb-3">{error}</p>}
        <div className="space-y-3">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Növbə adı *</label>
            <input
              type="text"
              value={form.name ?? ''}
              onChange={e => set('name', e.target.value)}
              placeholder="məs. Standart ofis, Dəstək növbəsi..."
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Növbə növü *</label>
            <select
              value={form.shiftType ?? ''}
              onChange={e => set('shiftType', e.target.value)}
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
            >
              <option value="">-- Növbə növü seçin --</option>
              {shiftTypeOptions.map(s => (
                <option key={s} value={s}>{SHIFT_TYPE_LABELS[s] ?? s}</option>
              ))}
            </select>
          </div>
          {!isFlexible && (
            <>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Başlanğıc vaxtı *</label>
                  <input type="time" value={form.startTime} onChange={e => set('startTime', e.target.value)} className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Bitmə vaxtı *</label>
                  <input type="time" value={form.endTime} onChange={e => set('endTime', e.target.value)} className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500" />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Fasilə (dəq.)</label>
                  <input
                    type="number"
                    min={0}
                    max={120}
                    value={form.breakMinutes ?? 0}
                    onChange={e => set('breakMinutes', Number(e.target.value))}
                    className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Gecikmə toleransı (dəq.)</label>
                  <select value={form.allowedLateMinutes ?? 10} onChange={e => set('allowedLateMinutes', Number(e.target.value))} className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500">
                    {LATE_OPTIONS.map(n => <option key={n} value={n}>{n}</option>)}
                  </select>
                </div>
              </div>
            </>
          )}
          {isFlexible && (
            <p className="text-sm text-slate-500 rounded-lg bg-slate-50 px-3 py-2">
              Sərbəst Növbədə sabit başlanğıc/bitmə vaxtı və gecikmə yoxdur. Yalnız iş intervalarının cəmi hesablanır.
            </p>
          )}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Açıqlama</label>
            <textarea value={form.description ?? ''} onChange={e => set('description', e.target.value)} rows={2} className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500" />
          </div>
        </div>
        <div className="flex gap-3 mt-5 justify-end">
          <button onClick={onClose} className="px-4 py-2 rounded-lg border text-gray-600 hover:bg-gray-50 text-sm">Ləğv et</button>
          <button onClick={handleSave} disabled={saving} className="px-4 py-2 rounded-lg text-white text-sm font-medium" style={{ background: '#a855f7' }}>
            {saving ? 'Saxlanılır...' : (initial?.id ? 'Yenilə' : 'Əlavə et')}
          </button>
        </div>
      </div>
    </div>
  )
}

function TimetableTab() {
  const { timetables, loading, fetchTimetables, createTimetable, updateTimetable, deleteTimetable } = useScheduleStore()
  const [showModal, setShowModal] = useState(false)
  const [editing, setEditing] = useState<Timetable | null>(null)
  const [confirmId, setConfirmId] = useState<number | null>(null)

  useEffect(() => { void fetchTimetables() }, [])

  const handleSave = async (data: Partial<Timetable>) => {
    if (editing) await updateTimetable(editing.id, data)
    else await createTimetable(data)
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <p className="text-sm text-gray-500">{timetables.length} qrafik</p>
        <button onClick={() => { setEditing(null); setShowModal(true) }} className="flex items-center gap-2 px-4 py-2 rounded-lg text-white text-sm font-medium" style={{ background: '#a855f7' }}>
          <span className="text-lg leading-none">+</span> Yeni qrafik
        </button>
      </div>

      {loading ? (
        <div className="flex justify-center py-12"><div className="w-8 h-8 border-4 border-purple-500 border-t-transparent rounded-full animate-spin" /></div>
      ) : timetables.length === 0 ? (
        <div className="text-center py-12 text-gray-400">
          <svg className="w-12 h-12 mx-auto mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M8 7V3m8 4V3M5 11h14M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" /></svg>
          <p>Heç bir iş qrafiki yoxdur</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {timetables.map(t => (
            <div key={t.id} className="bg-white rounded-xl border p-4 flex flex-col gap-2 shadow-sm">
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-2">
                  <div className="w-9 h-9 rounded-lg flex items-center justify-center" style={{ background: '#f3e8ff' }}>
                    <svg className="w-5 h-5" style={{ color: '#a855f7' }} fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3M5 11h14M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" /></svg>
                  </div>
                  <div>
                    <p className="font-semibold text-gray-800 text-sm">{t.name}</p>
                    {t.shiftType && (
                      <span className="text-xs text-purple-600 font-medium">
                        {SHIFT_TYPE_LABELS[t.shiftType] ?? t.shiftType}
                      </span>
                    )}
                  </div>
                </div>
                <div className="flex gap-1">
                  <button onClick={() => { setEditing(t); setShowModal(true) }} className="p-1.5 rounded-lg hover:bg-gray-100 text-gray-500">
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" /></svg>
                  </button>
                  <button onClick={() => setConfirmId(t.id)} className="p-1.5 rounded-lg hover:bg-red-50 text-red-400">
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" /></svg>
                  </button>
                </div>
              </div>
              <div className="text-xs text-gray-500 space-y-1">
                {['FLEXIBLE', 'FIRST_ENTRY', 'SERBEST', 'FREE_SHIFT', 'FREE'].includes((t.shiftType ?? '').toUpperCase()) ? (
                  <span>Sərbəst Növbə — sabit vaxt / gecikmə yoxdur</span>
                ) : (
                  <>
                    <div className="flex items-center gap-1">
                      <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
                      <span>{t.startTime} – {t.endTime}</span>
                    </div>
                    <div className="flex gap-3 flex-wrap">
                      {(t.breakMinutes ?? 0) > 0 && <span>Fasilə: <strong>{t.breakMinutes} dəq.</strong></span>}
                      <span>Gecikmə: <strong>{t.allowedLateMinutes ?? 0} dəq.</strong></span>
                    </div>
                  </>
                )}
              </div>
              {t.description && <p className="text-xs text-gray-400 truncate">{t.description}</p>}
            </div>
          ))}
        </div>
      )}

      {showModal && (
        <TimetableModal
          initial={editing ?? undefined}
          onSave={handleSave}
          onClose={() => { setShowModal(false); setEditing(null) }}
        />
      )}
      {confirmId !== null && (
        <ConfirmDialog
          message="Bu iş qrafikini silmək istədiyinizə əminsiniz?"
          onConfirm={async () => { await deleteTimetable(confirmId); setConfirmId(null) }}
          onCancel={() => setConfirmId(null)}
        />
      )}
    </div>
  )
}

// ─── Tab 2: Holidays ──────────────────────────────────────────────────────────
const defaultHoliday = (): Partial<Holiday> => ({
  name: 'Novruz Bayramı',
  description: '',
  holidayDate: new Date().toISOString().split('T')[0],
  applyScope: 'ALL',
  targetIds: [],
  scopeType: '',
})

function HolidayModal({ initial, onSave, onClose }: {
  initial?: Partial<Holiday>
  onSave: (data: Partial<Holiday>) => Promise<void>
  onClose: () => void
}) {
  const [form, setForm] = useState<Partial<Holiday>>(initial ?? defaultHoliday())
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [customName, setCustomName] = useState('')

  const set = (k: keyof Holiday, v: unknown) => setForm(f => ({ ...f, [k]: v }))

  const displayName = form.name === 'Xüsusi' ? customName : form.name

  const handleSave = async () => {
    if (!displayName) { setError('Ad seçilməlidir'); return }
    if (!form.holidayDate) { setError('Tarix seçilməlidir'); return }
    if ((form.applyScope === 'SPECIFIC_DEPARTMENT' || form.applyScope === 'SPECIFIC_BRANCH') && (!form.targetIds || form.targetIds.length === 0)) {
      setError('Hədəf seçilməlidir'); return
    }
    setSaving(true)
    try {
      await onSave({ ...form, name: displayName as string })
      onClose()
    } catch { setError('Xəta baş verdi') }
    finally { setSaving(false) }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-2xl shadow-xl p-6 w-full max-w-md">
        <h2 className="text-lg font-bold text-gray-900 mb-4">{initial?.id ? 'Bayramı redaktə et' : 'Yeni bayram günü'}</h2>
        {error && <p className="text-red-500 text-sm mb-3">{error}</p>}
        <div className="space-y-3">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Ad</label>
            <select value={form.name} onChange={e => set('name', e.target.value)} className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500">
              {DEFAULT_HOLIDAY_NAMES.map(n => <option key={n} value={n}>{n}</option>)}
            </select>
            {form.name === 'Xüsusi' && (
              <input value={customName} onChange={e => setCustomName(e.target.value)} placeholder="Xüsusi ad daxil edin" className="w-full border rounded-lg px-3 py-2 text-sm mt-2 focus:outline-none focus:ring-2 focus:ring-purple-500" />
            )}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Tarix</label>
            <input type="date" value={form.holidayDate} onChange={e => set('holidayDate', e.target.value)} className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Tətbiq dairəsi</label>
            <select value={form.applyScope} onChange={e => { set('applyScope', e.target.value); set('targetIds', []) }} className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500">
              {APPLY_SCOPES.map(s => <option key={s} value={s}>{APPLY_SCOPE_LABELS[s]}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Açıqlama</label>
            <textarea value={form.description ?? ''} onChange={e => set('description', e.target.value)} rows={2} className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500" />
          </div>
        </div>
        <div className="flex gap-3 mt-5 justify-end">
          <button onClick={onClose} className="px-4 py-2 rounded-lg border text-gray-600 hover:bg-gray-50 text-sm">Ləğv et</button>
          <button onClick={handleSave} disabled={saving} className="px-4 py-2 rounded-lg text-white text-sm font-medium" style={{ background: '#a855f7' }}>
            {saving ? 'Saxlanılır...' : (initial?.id ? 'Yenilə' : 'Əlavə et')}
          </button>
        </div>
      </div>
    </div>
  )
}

function HolidayTab() {
  const { holidays, loading, fetchHolidays, createHoliday, updateHoliday, deleteHoliday } = useScheduleStore()
  const [showModal, setShowModal] = useState(false)
  const [editing, setEditing] = useState<Holiday | null>(null)
  const [confirmId, setConfirmId] = useState<number | null>(null)

  useEffect(() => { void fetchHolidays() }, [])

  const handleSave = async (data: Partial<Holiday>) => {
    if (editing) await updateHoliday(editing.id, data)
    else await createHoliday(data)
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <p className="text-sm text-gray-500">{holidays.length} bayram</p>
        <button onClick={() => { setEditing(null); setShowModal(true) }} className="flex items-center gap-2 px-4 py-2 rounded-lg text-white text-sm font-medium" style={{ background: '#a855f7' }}>
          <span className="text-lg leading-none">+</span> Yeni bayram
        </button>
      </div>

      {loading ? (
        <div className="flex justify-center py-12"><div className="w-8 h-8 border-4 border-purple-500 border-t-transparent rounded-full animate-spin" /></div>
      ) : holidays.length === 0 ? (
        <div className="text-center py-12 text-gray-400">
          <svg className="w-12 h-12 mx-auto mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z" /></svg>
          <p>Heç bir bayram günü yoxdur</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {holidays.map(h => (
            <div key={h.id} className="bg-white rounded-xl border p-4 flex flex-col gap-2 shadow-sm">
              <div className="flex items-start justify-between">
                <div>
                  <p className="font-semibold text-gray-800 text-sm">{h.name}</p>
                  <p className="text-xs text-gray-500">{h.holidayDate}</p>
                </div>
                <div className="flex gap-1">
                  <button onClick={() => { setEditing(h); setShowModal(true) }} className="p-1.5 rounded-lg hover:bg-gray-100 text-gray-500">
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" /></svg>
                  </button>
                  <button onClick={() => setConfirmId(h.id)} className="p-1.5 rounded-lg hover:bg-red-50 text-red-400">
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" /></svg>
                  </button>
                </div>
              </div>
              {h.applyScope && (
                <span className={`self-start text-xs px-2 py-0.5 rounded-full font-medium ${SCOPE_COLORS[h.applyScope] ?? 'bg-gray-100 text-gray-600'}`}>
                  {APPLY_SCOPE_LABELS[h.applyScope] ?? h.applyScope}
                </span>
              )}
              {h.description && <p className="text-xs text-gray-400 truncate">{h.description}</p>}
            </div>
          ))}
        </div>
      )}

      {showModal && (
        <HolidayModal
          initial={editing ?? undefined}
          onSave={handleSave}
          onClose={() => { setShowModal(false); setEditing(null) }}
        />
      )}
      {confirmId !== null && (
        <ConfirmDialog
          message="Bu bayram gününü silmək istədiyinizə əminsiniz?"
          onConfirm={async () => { await deleteHoliday(confirmId); setConfirmId(null) }}
          onCancel={() => setConfirmId(null)}
        />
      )}
    </div>
  )
}

// ─── Tab 3: Permissions ───────────────────────────────────────────────────────
const defaultPermission = (): Partial<Permission> => ({
  name: '',
  description: '',
  leaveType: 'MEDICAL_LEAVE',
  applyType: 'EMPLOYEE',
  targetId: undefined,
  startDate: new Date().toISOString().split('T')[0],
  endDate: new Date().toISOString().split('T')[0],
  status: 'ACTIVE',
})

const PERMISSION_ICONS: Record<string, JSX.Element> = {
  MEDICAL_LEAVE: <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" /></svg>,
  REMOTE_WORK: <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" /></svg>,
}

const STATUS_COLORS: Record<string, string> = {
  ACTIVE: 'bg-green-100 text-green-700',
  INACTIVE: 'bg-gray-100 text-gray-600',
  PENDING: 'bg-yellow-100 text-yellow-700',
  APPROVED: 'bg-blue-100 text-blue-700',
  REJECTED: 'bg-red-100 text-red-600',
}

function PermissionModal({ initial, permissionTypes, onSave, onClose }: {
  initial?: Partial<Permission>
  permissionTypes: PermissionType[]
  onSave: (data: Partial<Permission>) => Promise<void>
  onClose: () => void
}) {
  const types = permissionTypes.length > 0 ? permissionTypes : DEFAULT_PERMISSION_TYPES
  const [form, setForm] = useState<Partial<Permission>>(initial ?? defaultPermission())
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const set = (k: keyof Permission, v: unknown) => setForm(f => ({ ...f, [k]: v }))

  const handleSave = async () => {
    if (!form.name?.trim()) { setError('Ad daxil edilməlidir'); return }
    if (!form.leaveType) { setError('İcazə növü seçilməlidir'); return }
    if (!form.startDate || !form.endDate) { setError('Tarix aralığı seçilməlidir'); return }
    if (form.endDate < form.startDate) { setError('Bitmə tarixi başlanğıc tarixindən əvvəl ola bilməz'); return }
    setSaving(true)
    try { await onSave(form); onClose() }
    catch { setError('Xəta baş verdi') }
    finally { setSaving(false) }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-2xl shadow-xl p-6 w-full max-w-md max-h-[90vh] overflow-y-auto">
        <h2 className="text-lg font-bold text-gray-900 mb-4">{initial?.id ? 'İcazəni redaktə et' : 'Yeni icazə'}</h2>
        {error && <p className="text-red-500 text-sm mb-3">{error}</p>}
        <div className="space-y-3">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Ad</label>
            <input value={form.name ?? ''} onChange={e => set('name', e.target.value)} className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500" placeholder="İcazə adı" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">İcazə növü</label>
            <select value={form.leaveType} onChange={e => set('leaveType', e.target.value)} className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500">
              {types.map(pt => <option key={pt.code} value={pt.code}>{LEAVE_TYPE_LABELS[pt.code] ?? pt.name}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Tətbiq növü</label>
            <select value={form.applyType} onChange={e => set('applyType', e.target.value)} className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500">
              {APPLY_TYPES.map(a => <option key={a} value={a}>{APPLY_TYPE_LABELS[a]}</option>)}
            </select>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Başlanğıc tarixi</label>
              <input type="date" value={form.startDate} onChange={e => set('startDate', e.target.value)} className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Bitmə tarixi</label>
              <input type="date" value={form.endDate} onChange={e => set('endDate', e.target.value)} className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500" />
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Vəziyyət</label>
            <select value={form.status ?? 'ACTIVE'} onChange={e => set('status', e.target.value)} className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500">
              {PERMISSION_STATUSES.map(s => <option key={s} value={s}>{statusLabel(s)}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Qeydlər</label>
            <textarea value={form.description ?? ''} onChange={e => set('description', e.target.value)} rows={2} className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500" />
          </div>
        </div>
        <div className="flex gap-3 mt-5 justify-end">
          <button onClick={onClose} className="px-4 py-2 rounded-lg border text-gray-600 hover:bg-gray-50 text-sm">Ləğv et</button>
          <button onClick={handleSave} disabled={saving} className="px-4 py-2 rounded-lg text-white text-sm font-medium" style={{ background: '#a855f7' }}>
            {saving ? 'Saxlanılır...' : (initial?.id ? 'Yenilə' : 'Əlavə et')}
          </button>
        </div>
      </div>
    </div>
  )
}

function PermissionTab() {
  const { permissions, permissionTypes, loading, fetchPermissions, fetchPermissionTypes, createPermission, updatePermission, deletePermission } = useScheduleStore()
  const [showModal, setShowModal] = useState(false)
  const [editing, setEditing] = useState<Permission | null>(null)
  const [confirmId, setConfirmId] = useState<number | null>(null)
  const types = permissionTypes.length > 0 ? permissionTypes : DEFAULT_PERMISSION_TYPES

  useEffect(() => {
    void fetchPermissions()
    void fetchPermissionTypes()
  }, [])

  const handleSave = async (data: Partial<Permission>) => {
    if (editing) await updatePermission(editing.id, data)
    else await createPermission(data)
  }

  const getTypeName = (code: string) =>
    LEAVE_TYPE_LABELS[code] ?? types.find(t => t.code === code)?.name ?? code

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <p className="text-sm text-gray-500">{permissions.length} icazə</p>
        <button onClick={() => { setEditing(null); setShowModal(true) }} className="flex items-center gap-2 px-4 py-2 rounded-lg text-white text-sm font-medium" style={{ background: '#a855f7' }}>
          <span className="text-lg leading-none">+</span> Yeni icazə
        </button>
      </div>

      {loading ? (
        <div className="flex justify-center py-12"><div className="w-8 h-8 border-4 border-purple-500 border-t-transparent rounded-full animate-spin" /></div>
      ) : permissions.length === 0 ? (
        <div className="text-center py-12 text-gray-400">
          <svg className="w-12 h-12 mx-auto mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" /></svg>
          <p>Heç bir icazə yoxdur</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {permissions.map(p => (
            <div key={p.id} className="bg-white rounded-xl border p-4 flex flex-col gap-2 shadow-sm">
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-2">
                  <div className="w-9 h-9 rounded-lg flex items-center justify-center" style={{ background: '#f3e8ff', color: '#a855f7' }}>
                    {PERMISSION_ICONS[p.leaveType] ?? (
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" /></svg>
                    )}
                  </div>
                  <div>
                    <p className="font-semibold text-gray-800 text-sm">{p.name}</p>
                    <p className="text-xs text-purple-600">{getTypeName(p.leaveType)}</p>
                  </div>
                </div>
                <div className="flex gap-1">
                  <button onClick={() => { setEditing(p); setShowModal(true) }} className="p-1.5 rounded-lg hover:bg-gray-100 text-gray-500">
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" /></svg>
                  </button>
                  <button onClick={() => setConfirmId(p.id)} className="p-1.5 rounded-lg hover:bg-red-50 text-red-400">
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" /></svg>
                  </button>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${STATUS_COLORS[p.status] ?? 'bg-gray-100 text-gray-600'}`}>
                  {statusLabel(p.status)}
                </span>
                <span className="text-xs text-gray-500">{APPLY_TYPE_LABELS[p.applyType] ?? p.applyType}</span>
              </div>
              <p className="text-xs text-gray-400">{p.startDate} → {p.endDate}</p>
              {p.description && <p className="text-xs text-gray-400 truncate">{p.description}</p>}
            </div>
          ))}
        </div>
      )}

      {showModal && (
        <PermissionModal
          initial={editing ?? undefined}
          permissionTypes={types}
          onSave={handleSave}
          onClose={() => { setShowModal(false); setEditing(null) }}
        />
      )}
      {confirmId !== null && (
        <ConfirmDialog
          message="Bu icazəni silmək istədiyinizə əminsiniz?"
          onConfirm={async () => { await deletePermission(confirmId); setConfirmId(null) }}
          onCancel={() => setConfirmId(null)}
        />
      )}
    </div>
  )
}

// ─── Main Page ────────────────────────────────────────────────────────────────
const TABS = [
  {
    key: 'timetables',
    label: 'İş qrafiki',
    icon: (
      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3M5 11h14M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
      </svg>
    ),
  },
  {
    key: 'shift-assignment',
    label: 'Növbə təyin',
    icon: (
      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
    ),
  },
]

export default function WorkSchedulePage() {
  const [activeTab, setActiveTab] = useState('timetables')

  return (
    <Layout>
      <div className="p-8 space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">İş Qrafiki</h1>
          <p className="text-sm text-gray-500 mt-1">İş qrafiklərini və növbə təyinatlarını idarə edin</p>
        </div>

        {/* Tabs */}
        <div className="border-b border-gray-200">
          <nav className="flex gap-1">
            {TABS.map(tab => (
              <button
                key={tab.key}
                onClick={() => setActiveTab(tab.key)}
                className={`flex items-center gap-2 px-4 py-3 text-sm font-medium border-b-2 transition-colors ${
                  activeTab === tab.key
                    ? 'border-purple-500 text-purple-600'
                    : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                }`}
              >
                {tab.icon}
                {tab.label}
              </button>
            ))}
          </nav>
        </div>

        {/* Tab Content */}
        <div>
          {activeTab === 'timetables' && <TimetableTab />}
          {activeTab === 'shift-assignment' && <ShiftAssignmentPage />}
        </div>
      </div>
    </Layout>
  )
}
