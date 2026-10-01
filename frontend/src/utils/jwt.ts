const MAX_TIMEOUT_MS = 2_147_483_647

interface JwtPayload {
  exp?: number
}

export function getTokenExpiryMs(token: string): number | null {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return null

    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')
    const bytes = Uint8Array.from(atob(padded), (character) => character.charCodeAt(0))
    const payload = JSON.parse(new TextDecoder().decode(bytes)) as JwtPayload

    return typeof payload.exp === 'number' ? payload.exp * 1000 : null
  } catch {
    return null
  }
}

export function hasUsableAccessToken(token: string | null): token is string {
  if (!token) return false
  const expiresAt = getTokenExpiryMs(token)
  return expiresAt !== null && expiresAt > Date.now()
}

export function getSessionTimerDelay(token: string): number | null {
  const expiresAt = getTokenExpiryMs(token)
  if (expiresAt === null) return null
  return Math.min(Math.max(expiresAt - Date.now(), 0), MAX_TIMEOUT_MS)
}
