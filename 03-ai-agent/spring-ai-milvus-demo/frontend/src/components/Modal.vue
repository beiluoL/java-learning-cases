<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    subtitle?: string
    width?: string
    position?: 'center' | 'right'
  }>(),
  {
    subtitle: '',
    width: '560px',
    position: 'center',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  close: []
}>()

function close() {
  emit('update:modelValue', false)
  emit('close')
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && props.modelValue) close()
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})

watch(
  () => props.modelValue,
  (open) => {
    document.body.style.overflow = open ? 'hidden' : ''
  },
)
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div
        v-if="modelValue"
        class="modal-overlay"
        :class="`modal-overlay--${position}`"
        @click.self="close"
      >
        <div
          class="modal"
          :class="`modal--${position}`"
          :style="{ width }"
          role="dialog"
          aria-modal="true"
        >
          <header class="modal__header">
            <div>
              <h3 class="modal__title">{{ title }}</h3>
              <p v-if="subtitle" class="modal__subtitle">{{ subtitle }}</p>
            </div>
            <button class="modal__close" type="button" aria-label="关闭" @click="close">×</button>
          </header>
          <div class="modal__body"><slot /></div>
          <footer v-if="$slots.footer" class="modal__footer"><slot name="footer" /></footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  background: rgba(17, 24, 39, 0.42);
}
.modal-overlay--center {
  align-items: center;
  justify-content: center;
  padding: var(--space-4);
}
.modal-overlay--right {
  align-items: stretch;
  justify-content: flex-end;
}

.modal {
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  box-shadow: var(--shadow-lg);
  min-height: 0;
}
.modal--center {
  max-width: calc(100vw - 32px);
  max-height: calc(100vh - 32px);
  border-radius: var(--radius-lg);
}
.modal--right {
  height: 100vh;
  max-width: 100vw;
  border-left: 1px solid var(--color-border);
}

.modal__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--color-border);
}
.modal__title {
  font-size: var(--font-lg);
  font-weight: var(--font-weight-semibold);
  letter-spacing: -0.01em;
}
.modal__subtitle {
  margin-top: 4px;
  font-size: var(--font-sm);
  color: var(--color-text-muted);
}
.modal__close {
  border: none;
  background: transparent;
  font-size: 20px;
  line-height: 1;
  padding: 2px 7px;
  border-radius: var(--radius-sm);
  color: var(--color-text-muted);
}
.modal__close:hover {
  background: var(--color-surface-hover);
  color: var(--color-text);
}
.modal__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--space-5);
}
.modal__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-5);
  border-top: 1px solid var(--color-border);
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity 180ms ease;
}
.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}
.modal-enter-active .modal,
.modal-leave-active .modal {
  transition: transform 180ms ease;
}
.modal-enter-from .modal--center,
.modal-leave-to .modal--center {
  transform: translateY(-8px) scale(0.985);
}
.modal-enter-from .modal--right,
.modal-leave-to .modal--right {
  transform: translateX(100%);
}
</style>
