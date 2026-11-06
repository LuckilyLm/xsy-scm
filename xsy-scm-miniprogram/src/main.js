import { createSSRApp } from 'vue';
import App from './App.vue';
import { store } from './store/index';
import { guardCurrentPage, installLoginGuard } from '@/lib/login-guard';
import './styles/global.scss';

export function createApp() {
  const app = createSSRApp(App);
  app.use(store);
  installLoginGuard();
  app.mixin({ onShow: guardCurrentPage });
  return { app, store };
}
