import { az } from './az.ts'

const dictionaries = { az } as const

type Locale = keyof typeof dictionaries
export type TranslationKey = NestedKeyOf<typeof az>

type NestedKeyOf<T extends object> = {
  [K in keyof T & string]: T[K] extends object
    ? `${K}.${NestedKeyOf<T[K]>}`
    : K
}[keyof T & string]

const defaultLocale: Locale = 'az'

function getValue(source: Record<string, unknown>, path: string): string | undefined {
  return path.split('.').reduce<unknown>((acc, part) => {
    if (acc && typeof acc === 'object' && part in (acc as Record<string, unknown>)) {
      return (acc as Record<string, unknown>)[part]
    }
    return undefined
  }, source) as string | undefined
}

export function t(key: TranslationKey, params?: Record<string, string | number>): string {
  const template = getValue(dictionaries[defaultLocale], key) ?? key
  if (!params) return template
  return Object.entries(params).reduce(
    (acc, [paramKey, value]) => acc.split(`{${paramKey}}`).join(String(value)),
    template
  )
}
