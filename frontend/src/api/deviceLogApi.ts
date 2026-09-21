import axios from 'axios'
import client from './client.ts'
import { ApiResponse } from '../types'

// ─────────────────────────────────────────────────────────────────────────────
// Type definitions
// ─────────────────────────────────────────────────────────────────────────────

export interface DeviceLogSearchParams {
  deviceId: number
  employeeId?: string
  name?: string
  cardNo?: string
  startTime?: string
  endTime?: string
  page?: number
  pageSize?: number
}

export interface DeviceLogEvent {
  employeeId?: string
  name?: string
  cardNo?: string
  eventDescription?: string
  time?: string
  hasPicture: boolean
  pictureURL?: string
  verifyMode?: string
}

export interface DeviceLogSearchResult {
  items: DeviceLogEvent[]
  totalMatches: number
  numOfMatches: number
  page: number
  pageSize: number
  /** "OK" = last page, "MORE" = more results exist, "NO MATCHES" = empty */
  responseStatus?: string
}

// ─────────────────────────────────────────────────────────────────────────────
// API functions
// ─────────────────────────────────────────────────────────────────────────────

export const deviceLogApi = {
  search: (params: DeviceLogSearchParams) =>
    client.get<ApiResponse<DeviceLogSearchResult>>('/device-logs/search', { params }),

  /**
   * Fetch a picture from the device via backend proxy (POST avoids @WEB URL encoding issues).
   * Returns a blob URL for use in an <img> src.
   */
  fetchPicture: async (pictureUrl: string, deviceId: number): Promise<string> => {
    try {
      const response = await client.post<Blob>(
        '/device-logs/picture',
        { url: pictureUrl, deviceId },
        { responseType: 'blob' },
      )

      const blob = response.data
      if (!blob || blob.size === 0) {
        throw new Error('Picture is no longer available on the device.')
      }

      const contentType = blob.type || 'image/jpeg'
      const imageBlob = blob.type ? blob : new Blob([blob], { type: contentType })
      return URL.createObjectURL(imageBlob)
    } catch (e: unknown) {
      if (axios.isAxiosError(e)) {
        if (e.response?.status === 404) {
          throw new Error('Picture is no longer available on the device.')
        }
        if (e.response?.status === 502) {
          throw new Error('Could not reach the device to fetch the photo.')
        }
        if (e.response?.data instanceof Blob) {
          try {
            const text = await e.response.data.text()
            const json = JSON.parse(text) as { message?: string }
            if (json.message) {
              throw new Error(json.message)
            }
          } catch (parseErr) {
            if (parseErr instanceof Error && parseErr.message !== 'Unexpected end of JSON input') {
              throw parseErr
            }
          }
        }
      }
      throw e instanceof Error ? e : new Error('Could not load photo')
    }
  },
}
