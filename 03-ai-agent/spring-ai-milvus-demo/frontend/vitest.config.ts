import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

/**
 * Vitest 配置。
 *
 * - environment: jsdom，因为要测组件渲染（utils 纯函数用同一套环境也无副作用）
 * - passWithNoTests: false —— 刻意关闭「无测试也算通过」，
 *   否则门禁在没有任何测试时会静默变绿，失去意义。
 *   新增模块必须带测试，否则 verify.sh 会失败。
 * - coverage 阈值暂不强制（存量测试覆盖不足），待 utils/composables 补齐后再开。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.spec.ts'],
    passWithNoTests: false,
  },
})
