import { apiGet } from './request';
import type { DeviceMetricSnapshot, AnalysisAlert, MetricStatVO } from './types';

export function fetchRealtimeSnapshots(deviceCode?: string) {
  return apiGet<DeviceMetricSnapshot[]>('/analysis/realtime', { deviceCode });
}

export function fetchAlerts(n = 20) {
  return apiGet<AnalysisAlert[]>('/analysis/alerts', { n });
}

export function fetchRules() {
  return apiGet<Record<string, { metric: string; min: number; max: number; unit: string }>>(
    '/analysis/rules',
  );
}
