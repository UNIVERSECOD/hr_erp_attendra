import { useEffect, useState } from 'react'

interface EmployeeAvatarProps {
  faceImageUrl?: string | null
  initials: string
  background: string
  sizeClass?: string
  textClass?: string
  alt?: string
}

function readAuthToken(): string | null {
  try {
    const authStorage = localStorage.getItem('auth-storage')
    if (!authStorage) return null
    const { state } = JSON.parse(authStorage)
    return state?.token ?? null
  } catch {
    return null
  }
}

/**
 * Loads employee profile photos through the backend API with JWT auth.
 * Falls back to initials when no photo URL exists or the image fails to load.
 */
export default function EmployeeAvatar({
  faceImageUrl,
  initials,
  background,
  sizeClass = 'w-9 h-9',
  textClass = 'text-xs',
  alt = '',
}: EmployeeAvatarProps) {
  const [src, setSrc] = useState<string | null>(null)

  useEffect(() => {
    let objectUrl: string | null = null
    let cancelled = false

    const load = async () => {
      if (!faceImageUrl) {
        setSrc(null)
        return
      }

      const apiBase = (import.meta.env.VITE_API_URL as string) || ''
      const imageUrl = faceImageUrl.startsWith('http')
        ? faceImageUrl
        : `${apiBase}${faceImageUrl}`
      const token = readAuthToken()

      try {
        const response = await fetch(imageUrl, {
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        })
        if (!response.ok) {
          if (!cancelled) setSrc(null)
          return
        }
        const blob = await response.blob()
        objectUrl = URL.createObjectURL(blob)
        if (!cancelled) setSrc(objectUrl)
      } catch {
        if (!cancelled) setSrc(null)
      }
    }

    void load()

    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [faceImageUrl])

  if (src) {
    return (
      <img
        src={src}
        alt={alt}
        className={`${sizeClass} rounded-full object-cover flex-shrink-0`}
      />
    )
  }

  return (
    <div
      className={`${sizeClass} rounded-full flex items-center justify-center text-white ${textClass} font-bold flex-shrink-0`}
      style={{ background }}
    >
      {initials}
    </div>
  )
}
