import { apiPostQuery } from './request';

/** 手动触发设备遥测模拟（参数以 query 传递）。 */
export function simulateDeviceData(count = 5, deviceId?: string, metric?: string) {
  return apiPostQuery<string>('/simulator/device-data', { count, deviceId, metric });
}

/** 手动触发舆情模拟。 */
export function simulateOpinion(count = 5) {
  return apiPostQuery<string>('/simulator/opinion', { count });
}
