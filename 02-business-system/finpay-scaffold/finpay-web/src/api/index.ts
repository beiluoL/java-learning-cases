import request from './request'

// 支付
export const payApi = {
  create: (data: any) => request.post('/api/pay/create', data),
  refund: (data: any) => request.post('/api/pay/refund', data),
  query: (params: any) => request.get('/api/pay/query', { params }),
  orders: (params: any) => request.get('/api/pay/orders', { params })
}

// 银行交易
export const bankApi = {
  open: (params: any) => request.post('/api/bank/open', null, { params }),
  transfer: (params: any) => request.post('/api/bank/transfer', null, { params }),
  accounts: () => request.get('/api/bank/accounts'),
  transactions: () => request.get('/api/bank/transactions')
}

// 对账
export const reconcileApi = {
  run: (params: any) => request.post('/api/admin/reconcile/run', null, { params }),
  retry: (params: any) => request.post('/api/admin/reconcile/retry', null, { params })
}

// 审计
export const auditApi = {
  list: (params: any) => request.get('/api/admin/audit/list', { params })
}

// 渠道配置
export const channelApi = {
  list: () => request.get('/api/admin/channel/list'),
  save: (data: any) => request.post('/api/admin/channel/save', data)
}

// 认证
export const authApi = {
  login: (data: any) => request.post('/auth/login', data)
}
