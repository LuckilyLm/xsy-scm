import { createSSRApp } from 'vue';
import App from './App.vue';
import { store } from './store/index';
import { guardCurrentPage, installLoginGuard } from '@/lib/login-guard';
import { useMallThemeStore } from '@/store/modules/mall/theme';
import { applyNativeTheme } from '@/utils/theme-native';
import './styles/global.scss';

export function createApp() {
  const app = createSSRApp(App);
  app.use(store);
  // 网络主题返回前，原生控件也使用同一份默认配色。
  applyNativeTheme(useMallThemeStore().theme);
  installLoginGuard();
  app.mixin({ onShow: guardCurrentPage });
  return { app, store };
}
