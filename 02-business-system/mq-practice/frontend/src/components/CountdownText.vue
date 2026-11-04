<template>
  <span class="countdown num" :class="{ danger: left <= 5 && left > 0, done: left <= 0 }">
    {{ display }}
  </span>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps<{
  /** 服务端计算的剩余秒数，每次列表刷新都会重新下发，天然校准 */
  seconds: number
  /** 只有待支付状态才需要走秒 */
  active?: boolean
}>()

const emit = defineEmits<{ (e: 'done'): void }>()

const left = ref(Math.max(0, props.seconds))
let timer: number | undefined

function start() {
  stop()
  if (!props.active) return
  timer = window.setInterval(() => {
    if (left.value > 0) {
      left.value -= 1
      if (left.value === 0) {
        stop()
        emit('done')
      }
    } else {
      stop()
    }
  }, 1000)
}

function stop() {
  if (timer) {
    window.clearInterval(timer)
    timer = undefined
  }
}

// 服务端每次下发新的 remainingSeconds，就重置本地倒计时（避免浏览器时钟漂移累积）
watch(
  () => props.seconds,
  (value) => {
    left.value = Math.max(0, value)
    start()
  },
  { immediate: true }
)

watch(() => props.active, start)

onBeforeUnmount(stop)

const display = computed(() => {
  if (!props.active) return '—'
  if (left.value <= 0) return '已到期'
  const m = Math.floor(left.value / 60)
  const s = left.value % 60
  return m > 0 ? `${m}:${String(s).padStart(2, '0')}` : `${s}s`
})
</script>

<style scoped>
.countdown {
  font-family: var(--font-mono);
  font-size: 12.5px;
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
}

.countdown.danger {
  color: var(--danger);
  font-weight: 650;
}

.countdown.done {
  color: var(--text-muted);
}
</style>
