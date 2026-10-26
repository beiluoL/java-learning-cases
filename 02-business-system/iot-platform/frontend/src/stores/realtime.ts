import { defineStore } from 'pinia';
import type { DeviceMetricSnapshot, AnalysisAlert, OpinionVO } from '@/api/types';

/** 实时数据状态：聚合 WebSocket 推送的设备指标、告警与舆情流。 */
export const useRealtimeStore = defineStore('realtime', {
  state: () => ({
    connected: false,
    /** key = deviceCode|metric */
    metrics: {} as Record<string, DeviceMetricSnapshot>,
    alerts: [] as AnalysisAlert[],
    opinions: [] as OpinionVO[],
  }),
  actions: {
    setConnected(value: boolean) {
      this.connected = value;
    },
    /** 处理设备指标推送（载荷为 DeviceDataMessage）。 */
    upsertMetric(msg: { deviceId: string; metric: string; value: number; unit?: string }) {
      const key = `${msg.deviceId}|${msg.metric}`;
      this.metrics[key] = {
        deviceCode: msg.deviceId,
        metric: msg.metric,
        unit: msg.unit ?? '',
        currentValue: msg.value,
        avg: msg.value,
        min: msg.value,
        max: msg.value,
        count: 0,
        status: 'NORMAL',
        lastUpdate: new Date().toISOString(),
      };
    },
    addAlert(alert: AnalysisAlert) {
      this.alerts.unshift(alert);
      if (this.alerts.length > 100) {
        this.alerts.pop();
      }
    },
    addOpinion(opinion: OpinionVO) {
      this.opinions.unshift(opinion);
      if (this.opinions.length > 50) {
        this.opinions.pop();
      }
    },
    metricList(): DeviceMetricSnapshot[] {
      return Object.values(this.metrics);
    },
  },
});
