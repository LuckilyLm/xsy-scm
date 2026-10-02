import {createApp, h, onMounted, ref} from 'vue';
import Antd from 'ant-design-vue';
import {setActivePinia} from 'pinia';
import {store} from '/@/store';
import smartEnumPlugin from '/@/plugins/smart-enums-plugin';
import constants from '/@/constants';
import 'ant-design-vue/dist/reset.css';
import '/@/theme/index.less';
setActivePinia(store);
const mode = new URLSearchParams(location.search).get('mode');
const component = mode === 'return'
  ? (await import('/@/views/business/scm/order/order-return-list.vue')).default
  : (await import('/@/views/business/scm/order/order-detail.vue')).default;
const app = createApp({setup() {
  const target = ref();
  onMounted(() => { if (mode !== 'return') target.value.open(1); });
  return () => h(component, {ref: target});
}});
app.use(store).use(Antd).use(smartEnumPlugin, constants);
// Permission enforcement has separate backend coverage; this fixture renders authorized controls.
app.directive('privilege', {});
app.mount('#app');
