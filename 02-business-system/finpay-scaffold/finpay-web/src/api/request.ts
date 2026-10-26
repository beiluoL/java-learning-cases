import axios from 'axios'
import { message } from 'ant-design-vue'
import { useAuthStore } from '@/store/auth'

// 统一请求实例：自动附加 JWT，统一处理业务码
const request = axios.create({
  baseURL: '/',
  timeout: 15000
})

request.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

request.interceptors.response.use(
  (resp) => {
    const data = resp.data
    if (data && data.code !== undefined && data.code !== 200) {
      message.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message))
    }
    return data
  },
  (err) => {
    if (err.response?.status === 401) {
      useAuthStore().logout()
      message.error('登录已失效，请重新登录')
    } else {
      message.error(err.message || '网络异常')
    }
    return Promise.reject(err)
  }
)

export default request
