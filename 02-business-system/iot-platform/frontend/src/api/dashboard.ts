import { apiGet } from './request';
import type {
  DashboardOverviewVO,
  MetricTrendPointVO,
  OpinionStatVO,
  OpinionTrendVO,
  HotTopicVO,
  StatusCountVO,
} from './types';

export function fetchOverview() {
  return apiGet<DashboardOverviewVO>('/dashboard/overview');
}

export function fetchOpinionOverview() {
  return apiGet<{ stat: OpinionStatVO; trend: OpinionTrendVO[]; hotTopics: HotTopicVO[] }>(
    '/dashboard/opinion',
  );
}

export function fetchDeviceStatus() {
  return apiGet<StatusCountVO[]>('/dashboard/device-status');
}

export function fetchMetricTrend(deviceCode: string, metric: string, hours = 6) {
  return apiGet<MetricTrendPointVO[]>('/dashboard/metric-trend', { deviceCode, metric, hours });
}

export function fetchAlerts(n = 20) {
  return apiGet<unknown[]>('/dashboard/alerts', { n });
}
