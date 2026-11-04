import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchSeckillProducts, resetStock, runStressTest, submitSeckillOrder } from '@/api/seckill'
import { fetchOrderDetail } from '@/api/order'
import type { OrderView, ProductView, SeckillRequest, SeckillResponse, StressTestResult } from '@/types'

/**
 * 场景一状态机：提交 → 轮询 → 拿到异步落单结果。
 *
 * <b>轮询为什么放在 store 而不是组件里？</b>
 * 因为轮询是"业务过程"而不是"视图逻辑"：组件卸载/切换页面时不应该把定时器留在后台跑，
 * 由 store 统一持有并暴露 stopPolling，配合 onBeforeUnmount 清理。
 */
export const useSeckillStore = defineStore('seckill', () => {
  const products = ref<ProductView[]>([])
  const loading = ref(false)
  const submitting = ref(false)

  /** 最近一次受理结果 */
  const latest = ref<SeckillResponse | null>(null)
  /** 轮询到的订单真实状态 */
  const latestOrder = ref<OrderView | null>(null)
  /** 轮询次数：用来直观展示"异步落单耗时几轮" */
  const pollCount = ref(0)
  const polling = ref(false)

  const stressResult = ref<StressTestResult | null>(null)
  const stressing = ref(false)

  let pollTimer: number | undefined

  async function loadProducts(silent = false) {
    if (!silent) loading.value = true
    try {
      products.value = await fetchSeckillProducts()
    } finally {
      loading.value = false
    }
  }

  async function submit(payload: SeckillRequest) {
    submitting.value = true
    latestOrder.value = null
    pollCount.value = 0
    try {
      const response = await submitSeckillOrder(payload)
      latest.value = response
      startPolling(response.orderNo)
      // 预扣后库存会变，立即刷新一次
      loadProducts(true)
      return response
    } finally {
      submitting.value = false
    }
  }

  /** 每 400ms 轮询一次，直到状态离开 QUEUING（说明消费者已经处理完） */
  function startPolling(orderNo: string) {
    stopPolling()
    polling.value = true
    pollTimer = window.setInterval(async () => {
      pollCount.value += 1
      try {
        const order = await fetchOrderDetail(orderNo)
        latestOrder.value = order
        if (order.status !== 'QUEUING') {
          stopPolling()
          loadProducts(true)
        }
      } catch {
        stopPolling()
      }
      // 最多轮询 30 次（12 秒），避免后端异常时无限请求
      if (pollCount.value >= 30) stopPolling()
    }, 400)
  }

  function stopPolling() {
    if (pollTimer) {
      window.clearInterval(pollTimer)
      pollTimer = undefined
    }
    polling.value = false
  }

  async function stress(productId: number, quantity: number, concurrency: number) {
    stressing.value = true
    try {
      stressResult.value = await runStressTest(productId, quantity, concurrency)
      await loadProducts(true)
      return stressResult.value
    } finally {
      stressing.value = false
    }
  }

  async function reset(productId: number) {
    await resetStock(productId)
    await loadProducts(true)
  }

  return {
    products,
    loading,
    submitting,
    latest,
    latestOrder,
    pollCount,
    polling,
    stressResult,
    stressing,
    loadProducts,
    submit,
    stopPolling,
    stress,
    reset
  }
})
