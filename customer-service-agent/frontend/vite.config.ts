import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// 前端开发服务器（默认 5173）将 /api 代理到后端（8080，context-path=/api）。
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // 关闭代理缓冲，保证 SSE 流式低延迟
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.setHeader('Connection', 'keep-alive');
          });
        }
      }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1500
  }
});
