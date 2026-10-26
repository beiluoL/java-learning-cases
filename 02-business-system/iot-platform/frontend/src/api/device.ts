import { apiGet, apiPost, apiDelete } from './request';
import type { DeviceVO, DeviceMetricSnapshot, PageResult, DeviceDataPointVO } from './types';

export function fetchDevices(params: {
  deviceCode?: string;
  deviceType?: string;
  status?: string;
  pageNum?: number;
  pageSize?: number;
}) {
  return apiGet<PageResult<DeviceVO>>('/devices', params as Record<string, unknown>);
}

export function saveDevice(body: {
  deviceCode: string;
  deviceName: string;
  deviceType?: string;
  protocol?: string;
  location?: string;
}) {
  return apiPost<DeviceVO>('/devices', body);
}

export function deleteDevice(id: number) {
  return apiDelete<void>(`/devices/${id}`);
}

export function fetchRealtime(deviceCode?: string) {
  return apiGet<DeviceMetricSnapshot[]>('/devices/realtime', { deviceCode });
}

export function fetchDataRange(deviceCode: string, metric: string, start?: string, end?: string) {
  return apiGet<DeviceDataPointVO[]>('/data/range', { deviceCode, metric, start, end });
}

export function fetchDataLatest(deviceCode: string, metric: string) {
  return apiGet<DeviceDataPointVO>('/data/latest', { deviceCode, metric });
}
