import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': '/src',
    },
  },
  server: {
    proxy: {
      // user-service (8082) — 与 nginx 路由保持一致
      '/api/v1/app-users': { target: 'http://localhost:8082', changeOrigin: true },
      '/api/user/':        { target: 'http://localhost:8082', changeOrigin: true },
      '/api/v1/user-tags': { target: 'http://localhost:8082', changeOrigin: true },
      '/api/v1/tag-categories': { target: 'http://localhost:8082', changeOrigin: true },
      '/api/v1/user-fields': { target: 'http://localhost:8082', changeOrigin: true },
      // agent-service (8090)
      '/api/agents': { target: 'http://localhost:8090', changeOrigin: true },
      '/api/chat':   { target: 'http://localhost:8090', changeOrigin: true },
      // admin-service (8081) — 兜底
      '/api': { target: 'http://localhost:8081', changeOrigin: true },
    },
  },
})
