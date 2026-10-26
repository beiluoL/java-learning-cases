import request from '../utils/request'

export function searchProducts(params) {
  return request.get('/products', { params })
}

export function getProductDetail(id) {
  return request.get(`/products/${id}`)
}

export function getCategoryTree() {
  return request.get('/categories')
}
