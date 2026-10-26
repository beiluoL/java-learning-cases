import request from '../utils/request'

export function addToCart(data) {
  return request.post('/cart/add', data)
}

export function updateCartQuantity(data) {
  return request.put('/cart/quantity', data)
}

export function checkCartItem(data) {
  return request.put('/cart/check', data)
}

export function removeCartItem(skuId) {
  return request.delete(`/cart/${skuId}`)
}

export function getCartList() {
  return request.get('/cart/list')
}
