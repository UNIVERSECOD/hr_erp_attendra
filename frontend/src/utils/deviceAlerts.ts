export const OFFLINE_REMINDER_MS = 30 * 60 * 1000
export interface HealthDevice { id: number; status?: string; online?: boolean }
export interface DeviceAlertState { offline: Record<string, number>; bridgeWarningAt?: number }

/** Deterministic reminder policy; callers persist this per signed-in user. */
export function evaluateDeviceAlerts(previous: DeviceAlertState, devices: HealthDevice[], available: boolean, now: number) {
  const state: DeviceAlertState = { offline: { ...previous.offline }, bridgeWarningAt: previous.bridgeWarningAt }
  const warn: number[] = [], recovered: number[] = []
  let bridgeWarning = false
  if (!available) {
    if (state.bridgeWarningAt === undefined || now - state.bridgeWarningAt >= OFFLINE_REMINDER_MS) {
      state.bridgeWarningAt = now
      bridgeWarning = true
    }
    return { state, warn, recovered, bridgeWarning }
  }
  delete state.bridgeWarningAt
  const active = devices.filter(d => d.status === 'ACTIVE')
  const ids = new Set(active.map(d => String(d.id)))
  for (const id of Object.keys(state.offline)) if (!ids.has(id)) delete state.offline[id]
  for (const device of active) {
    const last = state.offline[device.id]
    if (device.online === false && (last === undefined || now - last >= OFFLINE_REMINDER_MS)) {
      state.offline[device.id] = now
      warn.push(device.id)
    } else if (device.online === true && last !== undefined) {
      delete state.offline[device.id]
      recovered.push(device.id)
    }
  }
  return { state, warn, recovered, bridgeWarning }
}
