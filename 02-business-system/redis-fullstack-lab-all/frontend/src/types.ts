// 统一响应结构（与后端 R<T> 对应）
export interface R<T> {
  code: number
  msg: string
  data: T
}
