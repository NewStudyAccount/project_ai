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
      // /login 留给 portal 的 Vue 登录页，禁止代理到 9080（否则 GET /login 与 302 形成死循环）
      // 验密走 POST /api/login（经 /api 代理到 auth-service）
      '/api': { target: 'http://127.0.0.1:9080', changeOrigin: true },
      '/oauth2': { target: 'http://127.0.0.1:9080', changeOrigin: true },
      '/connect': { target: 'http://127.0.0.1:9080', changeOrigin: true },
    },
  },
})
