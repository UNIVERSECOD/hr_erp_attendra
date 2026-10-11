import { useCallback, useEffect, useRef, useState } from 'react'
import client from '../api/client.ts'
import { toast } from '../store/toastStore.ts'
import { formatAttendanceTime } from '../utils/dateTime.ts'

interface DayOverview {
  date: string
  updatedAt: string
  attended: number
  absent: number
  late: number
  earlyLeave: number
  permitted: number
  onLeave: number
  areas: { areaId: number; areaName: string; employeeCount: number }[]
  unknownArea: number
  unresolvedPresence: number
}

const cards = [
  { key: 'attended', label: 'İşdə olan', hint: 'Bu gün ən azı bir dəfə işdə olan əməkdaşlar', color: 'bg-emerald-50 text-emerald-700' },
  { key: 'absent', label: 'İşə gəlməyən', hint: 'Qrafik üzrə iş vaxtı başlayıb, giriş qeydi yoxdur', color: 'bg-red-50 text-red-700' },
  { key: 'late', label: 'İşə gecikən', hint: 'Qrafikin güzəşt müddəti və təsdiqlənmiş icazələr nəzərə alınır', color: 'bg-amber-50 text-amber-700' },
  { key: 'earlyLeave', label: 'İşdən tez çıxan', hint: 'Qrafik və icazələr nəzərə alınmaqla erkən çıxanlar', color: 'bg-orange-50 text-orange-700' },
  { key: 'permitted', label: 'İcazə alan', hint: 'Bu gün üçün təsdiqlənmiş saatlıq və ya tam günlük icazəsi olanlar', color: 'bg-purple-50 text-purple-700' },
  { key: 'onLeave', label: 'Məzuniyyətdə', hint: 'Bu günü əhatə edən təsdiqlənmiş məzuniyyəti olanlar', color: 'bg-pink-50 text-pink-700' },
] as const

export default function DashboardAttendancePanel({ totalEmployees }: { totalEmployees?: number }) {
  const [data, setData] = useState<DayOverview | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const mounted = useRef(false)
  const busy = useRef(false)

  const refresh = useCallback(async (manual = false) => {
    if (busy.current) return
    busy.current = true
    setLoading(true)
    try {
      const response = await client.get<{ data: DayOverview }>('/dashboard/attendance-overview')
      if (mounted.current) { setData(response.data.data); setError(false) }
    } catch {
      if (mounted.current) {
        setError(true)
        if (manual) toast.error('Davamiyyət xülasəsi yenilənmədi')
      }
    } finally {
      busy.current = false
      if (mounted.current) setLoading(false)
    }
  }, [])

  useEffect(() => {
    mounted.current = true
    void refresh()
    const timer = setInterval(() => { void refresh() }, 60_000)
    return () => { mounted.current = false; clearInterval(timer) }
  }, [refresh])

  return (
    <section className="bg-white rounded-xl shadow-sm p-4 sm:p-5 mb-6" aria-label="Bugünkü davamiyyət xülasəsi">
      <div className="flex flex-wrap items-center justify-between gap-2 mb-4">
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="text-base font-semibold text-gray-800">Bugünkü davamiyyət</h2>
          <span className="text-xs text-gray-400">Ümumi əməkdaş: {totalEmployees ?? '—'}</span>
        </div>
        <div className="flex items-center gap-3 text-xs text-gray-400">
          {data && <span>Yenilənib: {formatAttendanceTime(data.updatedAt)}</span>}
          <button type="button" onClick={() => { void refresh(true) }} disabled={loading}
            className="px-2.5 py-1.5 rounded-lg border border-gray-200 text-gray-600 hover:bg-gray-50 disabled:opacity-50">
            {loading ? 'Yenilənir…' : 'Yenilə'}
          </button>
        </div>
      </div>
      {error && <p role="alert" className="text-xs text-red-600 mb-3">Məlumat yenilənmədi.{data ? ' Son yüklənmiş saylar göstərilir.' : ' Yenidən yoxlayın.'}</p>}
      <div className="grid grid-cols-1 xl:grid-cols-3 gap-5">
        <div className="xl:col-span-2">
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5">
            {cards.map(card => <div key={card.key} title={card.hint} className={`rounded-lg px-3 py-2.5 ${card.color}`}>
              <p className="text-xs font-medium">{card.label}</p>
              <p className="text-2xl font-bold mt-1">{data ? data[card.key] : '—'}</p>
            </div>)}
          </div>
          <p className="text-xs text-gray-400 mt-3">Gün ərzindəki saylar • Eyni əməkdaş bir neçə göstəricidə ola bilər.</p>
        </div>
        <div className="xl:border-l xl:border-gray-100 xl:pl-5">
          <div className="flex items-center justify-between gap-2 mb-2">
            <h3 className="text-sm font-semibold text-gray-700">Hazırda ərazilərdə</h3>
            <span className="text-xs text-gray-400">nəfər</span>
          </div>
          <div className="space-y-1 max-h-40 overflow-y-auto">
            {!data ? <p className="text-sm text-gray-400">{loading ? 'Yüklənir…' : 'Məlumat yoxdur'}</p>
              : data.areas.length === 0 ? <p className="text-sm text-gray-400">Ərazi yoxdur</p>
                : data.areas.map(area => <div key={area.areaId} className="flex items-center justify-between gap-3 rounded-lg bg-gray-50 px-3 py-2 text-sm">
                  <span className="text-gray-600 truncate" title={area.areaName}>{area.areaName}</span>
                  <span className="font-semibold text-gray-800">{area.employeeCount}</span>
                </div>)}
            {!!data?.unknownArea && <p className="text-xs text-gray-500">Ərazisi məlum deyil: {data.unknownArea}</p>}
          </div>
          <p className="text-xs text-gray-400 mt-2">Son giriş–çıxış qeydinə əsasən • Hər dəqiqə yenilənir</p>
          {!!data?.unresolvedPresence && <p className="text-xs text-amber-700 mt-1">Köhnə çıxışsız qeyd: {data.unresolvedPresence} nəfər cari saya daxil deyil.</p>}
        </div>
      </div>
    </section>
  )
}
