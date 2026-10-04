import {createApp, h, onMounted, ref} from 'vue';
import Antd from 'ant-design-vue';
import {createMemoryHistory, createRouter} from 'vue-router';
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
// 视图里 useRoute() 读 route.name（退货列表靠它守深链 returnId）；不装 router 时 useRoute() 返回 undefined，
// 组件 setup 直接抛错、整页不渲染。这里补一个真实 router，让夹具的挂载环境和应用一致。
const router = createRouter({
  history: createMemoryHistory(),
  routes: [{path: '/:pathMatch(.*)*', name: 'adm-fixture', component: {render: (): null => null}}],
});
app.use(store).use(Antd).use(router).use(smartEnumPlugin, constants);
// Permission enforcement has separate backend coverage; this fixture renders authorized controls.
app.directive('privilege', {});
await router.push(location.pathname + location.search);
app.mount('#app');
