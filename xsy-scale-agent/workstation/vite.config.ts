import {defineConfig} from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()], base: './',
  server: {proxy: {'/api': 'http://127.0.0.1:33600'}},
  build: {sourcemap: false, emptyOutDir:false, chunkSizeWarningLimit: 1500}
});
