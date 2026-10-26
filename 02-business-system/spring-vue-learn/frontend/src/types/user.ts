// 用户类型定义（与后端 entity.User 对应）
export interface User {
  id: number
  username: string
  email: string
  createTime?: string
  updateTime?: string
  deleted?: number
}
