import { createSSRApp } from 'vue';
import App from './App.vue';
import { store } from './store/index';
import './styles/global.scss';

export function createApp() {
  const app = createSSRApp(App);
  app.use(store);
  return { app, store };
}
