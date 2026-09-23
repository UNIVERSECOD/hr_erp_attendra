import { TimetableDayRule } from '../types'

const DAY_LABELS = [
  'Bazar ertəsi',
  'Çərşənbə axşamı',
  'Çərşənbə',
  'Cümə axşamı',
  'Cümə',
  'Şənbə',
  'Bazar',
]

interface TimetableWeekEditorProps {
  rules: TimetableDayRule[]
  onChange: (rules: TimetableDayRule[]) => void
}

export default function TimetableWeekEditor({ rules, onChange }: TimetableWeekEditorProps) {
  const updateRule = (dayOfWeek: number, patch: Partial<TimetableDayRule>) => {
    onChange(rules.map(rule => rule.dayOfWeek === dayOfWeek ? { ...rule, ...patch } : rule))
  }

  return (
    <div className="overflow-x-auto rounded-lg border border-gray-200">
      <table className="w-full min-w-[920px] table-fixed text-sm">
        <thead className="bg-gray-50 text-xs font-semibold text-gray-500">
          <tr>
            <th className="w-40 px-3 py-2 text-left">Gün</th>
            <th className="w-24 px-3 py-2 text-center">İş günü</th>
            <th className="w-28 px-3 py-2 text-left">Başlanğıc</th>
            <th className="w-28 px-3 py-2 text-left">Bitmə</th>
            <th className="w-28 px-3 py-2 text-left">Fasilə</th>
            <th className="w-36 px-3 py-2 text-left">Giriş toleransı</th>
            <th className="w-36 px-3 py-2 text-left">Çıxış toleransı</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100 bg-white">
          {rules.map(rule => {
            const disabled = !rule.workingDay
            return (
              <tr key={rule.dayOfWeek} className={disabled ? 'bg-gray-50 text-gray-400' : 'text-gray-700'}>
                <td className="px-3 py-2 font-medium">{DAY_LABELS[rule.dayOfWeek - 1]}</td>
                <td className="px-3 py-2 text-center">
                  <input
                    type="checkbox"
                    checked={rule.workingDay}
                    onChange={event => updateRule(rule.dayOfWeek, { workingDay: event.target.checked })}
                    className="h-4 w-4 accent-purple-600"
                    aria-label={`${DAY_LABELS[rule.dayOfWeek - 1]} iş günüdür`}
                  />
                </td>
                <td className="px-3 py-2">
                  <input
                    type="time"
                    value={rule.startTime ?? '09:00'}
                    disabled={disabled}
                    onChange={event => updateRule(rule.dayOfWeek, { startTime: event.target.value })}
                    className="w-full rounded-md border border-gray-300 px-2 py-1.5 disabled:bg-gray-100"
                  />
                </td>
                <td className="px-3 py-2">
                  <input
                    type="time"
                    value={rule.endTime ?? '18:00'}
                    disabled={disabled}
                    onChange={event => updateRule(rule.dayOfWeek, { endTime: event.target.value })}
                    className="w-full rounded-md border border-gray-300 px-2 py-1.5 disabled:bg-gray-100"
                  />
                </td>
                <td className="px-3 py-2">
                  <input
                    type="number"
                    min={0}
                    max={1440}
                    value={rule.breakMinutes}
                    disabled={disabled}
                    onChange={event => updateRule(rule.dayOfWeek, { breakMinutes: Number(event.target.value) })}
                    className="w-full rounded-md border border-gray-300 px-2 py-1.5 disabled:bg-gray-100"
                    aria-label={`${DAY_LABELS[rule.dayOfWeek - 1]} fasilə dəqiqəsi`}
                  />
                </td>
                <td className="px-3 py-2">
                  <input
                    type="number"
                    min={0}
                    max={1440}
                    value={rule.allowedLateMinutes}
                    disabled={disabled}
                    onChange={event => updateRule(rule.dayOfWeek, { allowedLateMinutes: Number(event.target.value) })}
                    className="w-full rounded-md border border-gray-300 px-2 py-1.5 disabled:bg-gray-100"
                    aria-label={`${DAY_LABELS[rule.dayOfWeek - 1]} giriş toleransı`}
                  />
                </td>
                <td className="px-3 py-2">
                  <input
                    type="number"
                    min={0}
                    max={1440}
                    value={rule.allowedEarlyLeaveMinutes}
                    disabled={disabled}
                    onChange={event => updateRule(rule.dayOfWeek, { allowedEarlyLeaveMinutes: Number(event.target.value) })}
                    className="w-full rounded-md border border-gray-300 px-2 py-1.5 disabled:bg-gray-100"
                    aria-label={`${DAY_LABELS[rule.dayOfWeek - 1]} çıxış toleransı`}
                  />
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

export { DAY_LABELS }
