import request from '../utils/request'

export function getSeckillList() {
  return request.get('/seckill/activity/list')
}

export function getSeckillDetail(id) {
  return request.get(`/seckill/activity/${id}`)
}

export function doSeckill(activityId, skuId, quantity = 1) {
  return request.post(`/seckill/${activityId}/buy`, null, {
    params: { skuId, quantity }
  })
}
