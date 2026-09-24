import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 本地 dev 经 Vite 代理直打网关（CLAUDE.md 5.2：生产仍走 Nginx，不得绕过）
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': new URL('./src', import.meta.url).pathname,
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8173',
        changeOrigin: true,
      },
    },
  },
})
