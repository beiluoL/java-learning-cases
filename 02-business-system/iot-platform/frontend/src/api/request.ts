import axios from 'axios';
import { ElMessage } from 'element-plus';

/** 后端统一响应结构。 */
export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
  timestamp: string;
}

/** 后端分页结构：数据在 records 字段。 */
export interface PageResult<T> {
  records: T[];
  total: number;
  pageNum: number;
  pageSize: number;
  pages: number;
}

const instance = axios.create({
  baseURL: '/api',
  timeout: 10000,
});

instance.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResult<unknown>;
    if (body && typeof body.code === 'number' && body.code !== 200) {
      ElMessage.error(body.message || '请求失败');
      return Promise.reject(new Error(body.message));
    }
    return response;
  },
  (error) => {
    const msg = error.response?.data?.message || error.message || '网络错误';
    ElMessage.error(msg);
    return Promise.reject(error);
  },
);

export async function apiGet<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const resp = await instance.get<ApiResult<T>>(url, { params });
  return resp.data.data as T;
}

export async function apiPost<T>(url: string, data?: unknown): Promise<T> {
  const resp = await instance.post<ApiResult<T>>(url, data);
  return resp.data.data as T;
}

/** POST 请求，参数以 query string 形式传递（对应后端 @RequestParam）。 */
export async function apiPostQuery<T>(
  url: string,
  params?: Record<string, unknown>,
): Promise<T> {
  const resp = await instance.post<ApiResult<T>>(url, null, { params });
  return resp.data.data as T;
}

export async function apiDelete<T>(url: string): Promise<T> {
  const resp = await instance.delete<ApiResult<T>>(url);
  return resp.data.data as T;
}
