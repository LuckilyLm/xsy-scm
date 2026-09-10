import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    include: ['src/**/*.test.{ts,tsx}'],
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: true,
    globals: true,
    // jsdom 下 antd/ProTable 首次渲染较慢（模块转换 + 大量 DOM 计算），
    // 20s 在低速机器上会误判为超时，放宽到 60s 只影响上限而非预期耗时。
    testTimeout: 60_000,
  },
});
