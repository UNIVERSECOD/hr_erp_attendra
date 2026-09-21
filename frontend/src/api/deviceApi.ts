import client from './client.ts'
import { DeviceConfig } from '../types'

type DeviceWritePayload = Partial<DeviceConfig> & {
  password?: string
  deviceIp?: string
  deviceName?: string
  status?: string
}

const toIsapiUpsertPayload = (data: DeviceWritePayload) => ({
  ip: data.deviceIp,
  username: data.username,
  password: data.password,
  name: data.deviceName,
  enabled: data.status ? data.status === 'ACTIVE' : undefined,
  branchId: data.branchId,
})

export const deviceApi = {
  getAll: () => client.get<{ data: DeviceConfig[] }>('/devices'),
  create: (data: DeviceWritePayload) => client.post<{ data: DeviceConfig }>('/devices', toIsapiUpsertPayload(data)),
  update: (id: number, data: DeviceWritePayload) => client.put<{ data: DeviceConfig }>(`/devices/${id}`, toIsapiUpsertPayload(data)),
  delete: (id: number) => client.delete(`/devices/${id}`),
  sync: (id: number) => client.post(`/devices/${id}/sync`),
  getHistory: (id: number) => client.get(`/devices/${id}/history`),
  assignDoor: (id: number, data: { doorId?: number; role?: string }) =>
    client.post<{ data: DeviceConfig }>(`/devices/${id}/assign-door`, data),
}
