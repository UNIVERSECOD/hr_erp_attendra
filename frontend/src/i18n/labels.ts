import { t } from './index.ts'

/** Formal Azerbaijani labels for system roles. */
export function roleLabel(userType?: string | null): string {
  if (!userType) return '—'
  const key = `roles.${userType}` as const
  try {
    const label = t(key as never)
    if (label && label !== key) return label
  } catch {
    // fall through
  }
  switch (userType) {
    case 'HEAD_OFFICE_HR':
      return 'Baş ofis administratoru'
    case 'OFFICE_HR':
      return 'Ofis kadr mütəxəssisi'
    case 'DEPARTMENT_HR':
      return 'Departament kadr mütəxəssisi'
    case 'EMPLOYEE':
      return 'Əməkdaş'
    default:
      return userType.replace(/_/g, ' ')
  }
}

export function doorRoleLabel(role?: string | null): string {
  if (!role) return '—'
  const upper = role.toUpperCase()
  if (upper === 'ENTRY') return 'Giriş'
  if (upper === 'EXIT') return 'Çıxış'
  return role
}

export function statusLabel(status?: string | null): string {
  if (!status) return '—'
  switch (status.toUpperCase()) {
    case 'ACTIVE':
      return 'Aktiv'
    case 'INACTIVE':
      return 'Deaktiv'
    case 'PENDING':
      return 'Gözləmədə'
    case 'APPROVED':
      return 'Təsdiqlənib'
    case 'REJECTED':
      return 'Rədd edilib'
    case 'SUCCESS':
    case 'GRANTED':
      return 'Uğurlu'
    default:
      return status
  }
}
