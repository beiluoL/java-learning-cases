import { apiGet } from './request';
import type { OpinionVO, OpinionStatVO, OpinionTrendVO, HotTopicVO, PageResult } from './types';

export function fetchOpinionList(params: {
  sentiment?: string;
  topic?: string;
  source?: string;
  pageNum?: number;
  pageSize?: number;
}) {
  return apiGet<PageResult<OpinionVO>>('/opinion/list', params as Record<string, unknown>);
}

export function fetchOpinionStat() {
  return apiGet<OpinionStatVO>('/opinion/stat');
}

export function fetchOpinionTrend(days = 7) {
  return apiGet<OpinionTrendVO[]>('/opinion/trend', { days });
}

export function fetchHotTopics(days = 7, topN = 10) {
  return apiGet<HotTopicVO[]>('/opinion/hot', { days, topN });
}
