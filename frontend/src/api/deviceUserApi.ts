import client from './client.ts'

export interface DeviceUser {
  id: number
  employeeNo: string
  name: string
}

export interface DeviceFaceSyncResponse {
  status: 'SUCCESS' | 'FAILED' | 'NOT_FOUND'
  message: string
  faceUrl?: string
  imageBase64?: string
}

export const deviceUserApi = {
  getAll: (deviceId: number) => client.get<DeviceUser[]>(`/devices/${deviceId}/users`),
  uploadFace: (deviceId: number, userId: number, file: File, employeeId?: number) => {
    const formData = new FormData()
    formData.append('file', file)
    const query = employeeId ? `?employeeId=${employeeId}` : ''
    return client.post(`/devices/${deviceId}/users/${userId}/face${query}`, formData)
  },
  syncFaceFromDevice: (deviceId: number, userId: number, employeeId?: number) => {
    const query = employeeId ? `?employeeId=${employeeId}` : ''
    return client.post<DeviceFaceSyncResponse>(`/devices/${deviceId}/users/${userId}/face/sync${query}`)
  },
  deleteFace: (deviceId: number, userId: number, employeeId?: number) => {
    const query = employeeId ? `?employeeId=${employeeId}` : ''
    return client.delete(`/devices/${deviceId}/users/${userId}/face${query}`)
  },
}
