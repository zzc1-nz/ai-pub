import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  // sockjs-client（MessageList.vue 的 WebSocket 依赖）内部直接引用 Node 风格的裸
  // 标识符 global。生产构建时 Rollup 的 commonjs 插件会把它重写为 commonjsGlobal，
  // 但 dev 模式由 esbuild 预打包，不会重写 —— 实测产物中仍残留 79 处裸 global，
  // 运行时会抛 ReferenceError: global is not defined 导致消息页白屏。
  // 这里显式映射到 globalThis 修复。
  define: {
    global: 'globalThis'
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/wx': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // 开发模式下缺少以下两项会导致：
      //   /ws      -> SockJS 连接 404，消息页实时推送不可用
      //   /uploads -> 素材图片 404，预览全部裂图
      // （nginx.conf 中已正确配置，此前只有 dev 环境缺失）
      '/ws': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
