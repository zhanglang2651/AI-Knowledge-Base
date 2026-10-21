import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * Vite 配置。
 * <p>
 * 开发阶段通过 proxy 把 /api 转发到后端 8080，浏览器视角下前后端同源，
 * 因此后端无需配置 CORS。生产部署时由 Nginx 把 /api 反向代理到后端，
 * 同样不需要 CORS —— 只有前后端分域部署时才需要放开，那种情况应显式配置白名单，
 * 绝不能用 Access-Control-Allow-Origin: * 搭配 Cookie 凭证。
 */
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: false,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/actuator': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    // 拆包：antd 体积较大且很少变动，单独分出可长期命中浏览器缓存
    rollupOptions: {
      output: {
        manualChunks: {
          antd: ['antd', '@ant-design/icons'],
          react: ['react', 'react-dom', 'react-router-dom'],
        },
      },
    },
  },
});
