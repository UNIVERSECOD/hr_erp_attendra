/**
 * Determines whether a device should be considered "online".
 *
 * Rules:
 *  1. If status is 'INACTIVE', the device is offline.
 *  2. If lastSyncTime is missing/null/undefined, the device is offline.
 *  3. If lastSyncTime is present, the device is online ONLY if lastSyncTime is within ONLINE_THRESHOLD_MINUTES (10 mins).
 */

/** Minutes within which a device is considered online */
export const ONLINE_THRESHOLD_MINUTES = 10

export function isDeviceOnline(
  status: string | undefined,
  lastSyncTime: string | undefined,
): boolean {
  if (status === 'INACTIVE') return false
  if (!lastSyncTime) return false

  const syncDate = new Date(lastSyncTime)
  if (isNaN(syncDate.getTime())) return false

  const diffMs = Date.now() - syncDate.getTime()
  const diffMinutes = diffMs / 1000 / 60

  // Allow up to -5 mins skew (if server clock is slightly ahead) up to ONLINE_THRESHOLD_MINUTES
  return diffMinutes >= -5 && diffMinutes <= ONLINE_THRESHOLD_MINUTES
}

/**
 * Returns a human-readable relative time string for the last sync time.
 * e.g. "2 dəq əvvəl", "1 saat əvvəl", "3 gün əvvəl"
 */
export function relativeTime(lastSyncTime: string | undefined): string {
  if (!lastSyncTime) return 'Heç vaxt'
  const syncDate = new Date(lastSyncTime)
  if (isNaN(syncDate.getTime())) return 'Heç vaxt'

  const diffMs = Date.now() - syncDate.getTime()
  if (diffMs < 0) return 'İndi'

  const diffSec = Math.floor(diffMs / 1000)
  if (diffSec < 60) return `${diffSec} san əvvəl`
  const diffMin = Math.floor(diffSec / 60)
  if (diffMin < 60) return `${diffMin} dəq əvvəl`
  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `${diffHour} saat əvvəl`
  const diffDay = Math.floor(diffHour / 24)
  return `${diffDay} gün əvvəl`
}
