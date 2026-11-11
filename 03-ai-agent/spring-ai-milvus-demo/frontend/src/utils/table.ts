/** DataTable 列定义（放在独立 ts 文件，避免在 <script setup> 中使用 export） */
export interface TableColumn {
  key: string
  title: string
  align?: 'left' | 'right' | 'center'
  width?: string
  sortable?: boolean
  /** 纯文本格式化；复杂单元格请用 #cell-<key> 作用域插槽 */
  render?: (value: unknown, row: unknown) => string
}
