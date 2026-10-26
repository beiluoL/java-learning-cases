import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

// 统一请求封装：自动携带 JWT，统一处理 401 / 业务错误
const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers['Authorization'] = 'Bearer ' + token
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body.code === 200) {
      return body.data
    }
    if (body.code === 401) {
      localStorage.clear()
      ElMessage.error('登录已失效，请重新登录')
      router.push('/login')
      return Promise.reject(new Error(body.message))
    }
    if (body.code === 403) {
      ElMessage.error('无权限访问')
      return Promise.reject(new Error(body.message))
    }
    ElMessage.error(body.message || '请求失败')
    return Promise.reject(new Error(body.message))
  },
  (error) => {
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default request
