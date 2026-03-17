import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'
import type { Connect } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    // Dev 模式下模拟 nginx 的 MPA 路由重写：/platform/* → platform.html
    {
      name: 'mpa-dev-router',
      configureServer(server) {
        server.middlewares.use((req: Connect.IncomingMessage, _res, next) => {
          const url = req.url ?? '/'
          if (
            url.startsWith('/platform') &&
            !url.startsWith('/platform.html') &&
            !url.startsWith('/src/') &&
            !url.startsWith('/node_modules/') &&
            !url.startsWith('/@') &&
            !url.startsWith('/api/')
          ) {
            req.url = '/platform.html'
          }
          next()
        })
      },
    },
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  build: {
    rollupOptions: {
      input: {
        tenant: path.resolve(__dirname, 'index.html'),
        platform: path.resolve(__dirname, 'platform.html'),
      },
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
