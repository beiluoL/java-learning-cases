import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // 5173 被其他项目占用，本平台固定 5174，端口被占用时直接报错而非自动漂移。
    port: 5174,
    strictPort: true,
    proxy: {
      // 后端服务端口为 8091（8080 属于其他项目，严禁指向）。
      '/api': {
        // 显式用 IPv4，避免 Node 把 localhost 解析成 ::1 时与后端监听地址不一致
        target: 'http://127.0.0.1:8091',
        changeOrigin: true,
      },
    },
  },
})
