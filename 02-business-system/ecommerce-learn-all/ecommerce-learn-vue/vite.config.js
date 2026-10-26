import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 后端接口前缀：开发环境通过 Vite 代理转发到 8080，避免跨域
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
