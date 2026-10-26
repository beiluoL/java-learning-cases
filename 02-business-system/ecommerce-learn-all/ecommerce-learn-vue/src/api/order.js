import request from '../utils/request'

export function createOrder(data) {
  return request.post('/orders/create', data)
}

export function payOrder(orderId, channel = 'alipay') {
  return request.post(`/orders/${orderId}/pay`, null, { params: { channel } })
}

export function cancelOrder(orderId) {
  return request.post(`/orders/${orderId}/cancel`)
}

export function confirmOrder(orderId) {
  return request.post(`/orders/${orderId}/confirm`)
}

export function getMyOrders(params) {
  return request.get('/orders/my', { params })
}

export function getOrderDetail(id) {
  return request.get(`/orders/${id}`)
}
