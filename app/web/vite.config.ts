import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import type { ProxyOptions } from 'vite';
import { defineConfig } from 'vitest/config';

const BACKEND_ORIGIN = process.env.TMS_BACKEND_ORIGIN ?? 'http://localhost:8080';

/**
 * 事件串流是一條不關的連線。後端行程中途消失時，代理預設不會把瀏覽器這一頭的回應關掉，畫面會以為串流還連著。
 * 後端那一頭沒有正常收尾就斷了，就把瀏覽器這一頭也切斷，讓前端馬上知道並重連。
 */
const streamProxy: ProxyOptions = {
  target: BACKEND_ORIGIN,
  changeOrigin: true,
  configure: (proxy) => {
    proxy.on('proxyRes', (proxyRes, _req, res) => {
      proxyRes.on('close', () => {
        if (!proxyRes.complete) res.destroy();
      });
    });
  },
};

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/api': { target: BACKEND_ORIGIN, changeOrigin: true },
      '/staff/stream': streamProxy,
      // 先比對到的規則生效，串流那條要排在 `/guest` 前面。
      '/guest/stream': streamProxy,
      '/guest': { target: BACKEND_ORIGIN, changeOrigin: true },
    },
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
    passWithNoTests: true,
  },
});
