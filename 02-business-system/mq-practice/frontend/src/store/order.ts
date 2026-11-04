import { defineStore } from 'pinia'
import { reactive, ref } from 'vue'
import { cancelOrder, createDemoOrder, fetchOrderPage, payOrder } from '@/api/order'
import type { OrderView, SeckillRequest, SeckillResponse } from '@/types'

export const useOrderStore = defineStore('order', () => {
  const loading = ref(false)
  const records = ref<OrderView[]>([])
  const total = ref(0)

  const query = reactive({
    status: '',
    keyword: '',
    page: 1,
    size: 10
  })

  async function load(silent = false) {
    if (!silent) loading.value = true
    try {
      const data = await fetchOrderPage({ ...query })
      records.value = data.records
      total.value = data.total
    } finally {
      loading.value = false
    }
  }

  async function pay(orderNo: string) {
    const order = await payOrder(orderNo)
    await load(true)
    return order
  }

  async function cancel(orderNo: string) {
    const order = await cancelOrder(orderNo)
    await load(true)
    return order
  }

  async function createDemo(payload: SeckillRequest): Promise<SeckillResponse> {
    const response = await createDemoOrder(payload)
    await load(true)
    return response
  }

  function changePage(page: number) {
    query.page = page
    return load()
  }

  return { loading, records, total, query, load, pay, cancel, createDemo, changePage }
})
