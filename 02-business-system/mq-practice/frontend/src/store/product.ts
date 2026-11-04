import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { changeProduct, fetchProductSnapshot, fetchProducts } from '@/api/product'
import type { ProductChangeRequest, ProductChangeResult, ProductView } from '@/types'

export const useProductStore = defineStore('product', () => {
  const products = ref<ProductView[]>([])
  const loading = ref(false)
  const selectedId = ref<number | null>(null)
  const snapshot = ref<ProductView | null>(null)
  const snapshotLoading = ref(false)
  const lastChange = ref<ProductChangeResult | null>(null)

  const selected = computed(() => snapshot.value)

  async function load(silent = false) {
    if (!silent) loading.value = true
    try {
      products.value = await fetchProducts()
      if (selectedId.value === null && products.value.length > 0) {
        selectedId.value = products.value[0].id
      }
    } finally {
      loading.value = false
    }
  }

  async function select(id: number) {
    selectedId.value = id
    await refreshSnapshot()
  }

  async function refreshSnapshot() {
    if (selectedId.value === null) return
    snapshotLoading.value = true
    try {
      snapshot.value = await fetchProductSnapshot(selectedId.value)
    } finally {
      snapshotLoading.value = false
    }
  }

  async function change(payload: ProductChangeRequest) {
    if (selectedId.value === null) throw new Error('未选择商品')
    const result = await changeProduct(selectedId.value, payload)
    lastChange.value = result
    await load(true)
    // 立即读一次（大概率还没消费），再隔一段时间读两次（消费完成），
    // 这几次快照的差异就是"最终一致性窗口"，在页面上直接可见。
    await refreshSnapshot()
    window.setTimeout(() => refreshSnapshot(), 700)
    window.setTimeout(() => refreshSnapshot(), 2200)
    return result
  }

  return {
    products,
    loading,
    selectedId,
    snapshot,
    snapshotLoading,
    selected,
    lastChange,
    load,
    select,
    refreshSnapshot,
    change
  }
})
