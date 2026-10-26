/** 设备档案视图对象。 */
export interface DeviceVO {
  id: number;
  deviceCode: string;
  deviceName: string;
  deviceType: string;
  protocol: string;
  location: string;
  status: string;
  lastCollectTime: string | null;
  createTime: string | null;
}

/** 设备数据点视图对象。 */
export interface DeviceDataPointVO {
  deviceCode: string;
  metric: string;
  value: number;
  unit: string;
  collectTime: string;
}

/** 设备指标实时快照。 */
export interface DeviceMetricSnapshot {
  deviceCode: string;
  metric: string;
  unit: string;
  currentValue: number;
  avg: number;
  min: number;
  max: number;
  count: number;
  status: string;
  lastUpdate: string;
}

/** 分析告警。 */
export interface AnalysisAlert {
  alertId: string;
  deviceCode: string;
  metric: string;
  value: number;
  thresholdDesc: string;
  level: 'INFO' | 'WARN' | 'CRITICAL';
  message: string;
  time: string;
}

/** 指标聚合统计。 */
export interface MetricStatVO {
  deviceCode?: string;
  metric: string;
  avg: number;
  min: number;
  max: number;
  count: number;
}

/** 舆情记录。 */
export interface OpinionVO {
  id: number;
  source: string;
  author: string;
  content: string;
  sentiment: 'POSITIVE' | 'NEGATIVE' | 'NEUTRAL';
  sentimentScore: number;
  topic: string;
  hot: number;
  publishTime: string | null;
  url: string | null;
}

/** 舆情统计。 */
export interface OpinionStatVO {
  total: number;
  positive: number;
  negative: number;
  neutral: number;
  negativeRatio: number;
  sentimentIndex: number;
}

/** 舆情趋势点。 */
export interface OpinionTrendVO {
  date: string;
  positive: number;
  negative: number;
  neutral: number;
}

/** 热点话题。 */
export interface HotTopicVO {
  topic: string;
  count: number;
  hot: number;
}

/** 总览看板数据。 */
export interface DashboardOverviewVO {
  deviceTotal: number;
  deviceOnline: number;
  deviceOffline: number;
  dataPointCount24h: number;
  alertCount: number;
  opinionTotal: number;
  sentimentIndex: number;
  time: string;
}

/** 指标趋势点。 */
export interface MetricTrendPointVO {
  time: string;
  value: number;
}

/** 状态计数。 */
export interface StatusCountVO {
  status: string;
  count: number;
}
