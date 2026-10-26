import request from '../utils/request'

export function getDashboardStats() {
  return request.get('/admin/dashboard/stats')
}

export function getAdminOrders(params) {
  return request.get('/admin/orders/list', { params })
}

export function adminShip(orderId, data) {
  return request.post(`/admin/orders/${orderId}/ship`, data)
}

export function saveProduct(data) {
  return request.post('/admin/product/save', data)
}

export function deleteProduct(id) {
  return request.delete(`/admin/product/${id}`)
}

export function saveSeckill(data) {
  return request.post('/admin/seckill/save', data)
}
