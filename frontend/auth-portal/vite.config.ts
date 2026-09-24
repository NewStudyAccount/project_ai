import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 本地 dev 经 Vite 代理直打 auth-service（生产走 Nginx，见 CLAUDE.md 5.2）
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': new URL('./src', import.meta.url).pathname,
    },
  },
  server: {
    port: 5174,
    proxy: {
      '/api': { target: 'http://127.0.0.1:9080', changeOrigin: true },
      '/login': { target: 'http://127.0.0.1:9080', changeOrigin: true },
      '/oauth2': { target: 'http://127.0.0.1:9080', changeOrigin: true },
    },
  },
})
