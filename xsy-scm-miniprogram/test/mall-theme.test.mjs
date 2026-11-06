import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
import { createPinia, defineStore, setActivePinia } from 'pinia';

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');
const colors = await import(`data:text/javascript;base64,${Buffer.from(read('../src/constants/theme-color-const.js')).toString('base64')}`);
const visuals = await import(`data:text/javascript;base64,${Buffer.from(read('../src/constants/mall-visual-const.js')).toString('base64')}`);

function createStore(fetchTheme) {
  setActivePinia(createPinia());
  const native = [];
  const source = read('../src/store/modules/mall/theme.js')
    .replace(/^[^\S\r\n]*import[\s\S]*?;\r?\n/gm, '')
    .replace('export const useMallThemeStore', 'globalThis.useMallThemeStore');
  const context = vm.createContext({
    defineStore,
    ...colors,
    mallHomeApi: { theme: fetchTheme },
    xsySentry: { captureError() {} },
    applyNativeTheme: (code) => native.push(code),
  });
  vm.runInContext(source, context);
  return { store: context.useMallThemeStore(), native };
}

test('theme follows published releases including rollback and fails closed to FRESH', async () => {
  let data = { releaseId: 1, themeCode: 'WARM' };
  const { store } = createStore(async () => ({ data }));
  assert.equal(store.themeCode, 'FRESH');
  for (const [releaseId, themeCode] of [
    [1, 'WARM'],
    [2, 'OCEAN'],
    [3, 'WARM'],
  ]) {
    data = { releaseId, themeCode };
    await store.refresh();
    assert.equal(store.themeCode, themeCode);
    assert.equal(store.releaseId, releaseId);
    assert.equal(store.themeStyle['--mall-primary'], colors.MALL_THEMES[themeCode].primary);
  }
  for (const value of [{ releaseId: 4, themeCode: 'UNKNOWN' }, { releaseId: 5, themeCode: null }, { releaseId: null, themeCode: 'WARM' }, {}]) {
    data = value;
    await store.refresh();
    assert.equal(store.themeCode, 'FRESH');
  }
});

test('server-derived custom palette overrides preset; malformed palette falls back to preset', async () => {
  const data = {
    releaseId: 9,
    themeCode: 'WARM',
    palette: { primary: '#123456', dark: '#0d2b40', ink: '#0b1f2e', light: '#eef4f7', disabled: '#8a9aa6' },
  };
  const { store } = createStore(async () => ({ data }));
  await store.refresh();
  assert.equal(store.theme.primary, '#123456');
  assert.equal(store.themeStyle['--mall-primary-light'], '#eef4f7');

  data.palette = { ...data.palette, dark: 'red' };
  await store.refresh();
  assert.equal(store.theme.primary, colors.MALL_THEMES.WARM.primary);

  data.palette = null;
  await store.refresh();
  assert.equal(store.theme.primary, colors.MALL_THEMES.WARM.primary);
});

test('parallel page refreshes share one request; failed refresh clears a prior theme', async () => {
  let finish;
  let calls = 0;
  const { store } = createStore(() => {
    calls++;
    return new Promise((resolve, reject) => {
      finish = { resolve, reject };
    });
  });
  const first = store.refresh();
  const second = store.refresh();
  assert.equal(calls, 1);
  finish.resolve({ data: { releaseId: 1, themeCode: 'OCEAN' } });
  await Promise.all([first, second]);
  const failed = store.refresh();
  finish.reject(new Error('offline'));
  await failed;
  assert.equal(store.themeCode, 'FRESH');
  assert.equal(store.releaseId, null);
});

test('obsolete response cannot overwrite newer state', async () => {
  let finish;
  const { store } = createStore(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      })
  );
  const request = store.refresh();
  store.requestSeq++;
  store.applyTheme({ releaseId: 2, themeCode: 'OCEAN' });
  finish({ data: { releaseId: 1, themeCode: 'WARM' } });
  await request;
  assert.equal(store.themeCode, 'OCEAN');
});

test('every registered page participates in theme lifecycle, including direct subpackage routes', () => {
  const config = JSON.parse(read('../src/pages.json'));
  const routes = [
    ...config.pages.map((page) => page.path),
    ...config.subPackages.flatMap((pkg) => pkg.pages.map((page) => `${pkg.root}/${page.path}`)),
  ];
  assert.equal(routes.length, 29);
  for (const route of routes) {
    const source = read(`../src/${route}.vue`);
    assert.match(source, /useMallTheme\(\)/, route);
    assert.match(source, /:style="themeStyle"/, route);
  }
  assert.match(read('../src/composables/use-mall-theme.js'), /onShow\(/);
  assert.match(read('../src/App.vue'), /onShow:/);
  assert.doesNotMatch(read('../src/pages/home/index.vue'), /themeCode\.value\s*=/);
});

test('底部导航使用交付的成对PNG，路由与原生栏隐藏仍由公共组件维护', () => {
  // 原生 TabBar 只保留颜色同步：自绘导航挂载后原生栏会被隐藏
  const calls = [];
  const source = read('../src/utils/theme-native.js')
    .replace(/^[^\S\r\n]*import[\s\S]*?;\r?\n/gm, '')
    .replace('export function applyNativeTheme', 'function applyNativeTheme');
  const context = vm.createContext({ ...colors, uni: { setTabBarStyle: (options) => calls.push(options) } });
  vm.runInContext(source, context);
  context.applyNativeTheme(colors.MALL_THEMES.WARM);
  context.applyNativeTheme({ primary: '#123456' });
  assert.equal(calls.length, 2);
  assert.equal(calls[0].selectedColor, colors.MALL_THEMES.WARM.primary);
  assert.equal(calls[1].selectedColor, '#123456');

  const bar = read('../src/components/common/xsy-tab-bar.vue');
  for (const key of ['home', 'category', 'cart', 'orders', 'mine']) {
    const pair = visuals.MALL_NAVIGATION_IMAGES[key];
    assert.notEqual(pair.normal, pair.active);
    for (const path of [pair.normal, pair.active]) {
      assert.ok(existsSync(new URL(`../src${path}`, import.meta.url)), `导航素材缺失 ${path}`);
    }
  }
  assert.match(bar, /index === active \? item\.activeImage : item\.image/);
  assert.match(bar, /<image[^>]*mode="aspectFit"/);
  const props = { active: 0 };
  const switched = [];
  let mounted;
  const barContext = vm.createContext({
    ...visuals,
    defineProps: () => props,
    useMallTheme: () => ({ themeStyle: {} }),
    onMounted: (handler) => {
      mounted = handler;
    },
    uni: { switchTab: ({ url }) => switched.push(url), hideTabBar: () => switched.push('native-hidden') },
  });
  const script = bar
    .match(/<script setup>([\s\S]*?)<\/script>/)[1]
    .trimStart()
    .replace(/^[^\S\r\n]*import[\s\S]*?;\r?\n/gm, '');
  vm.runInContext(script + '\nglobalThis.entries = items;', barContext);
  mounted();
  const routes = ['/pages/home/index', '/pages/category/index', '/pages/cart/index', '/pages/order/index', '/pages/mine/index'];
  assert.deepEqual(
    Array.from(barContext.entries, (item) => item.path),
    routes
  );
  barContext.entries.forEach((item, index) => barContext.go(index, item.path));
  assert.deepEqual(switched, ['native-hidden', ...routes.slice(1)]);
  assert.doesNotMatch(read('../src/pages.json'), /static\/images/);
  assert.doesNotMatch(read('../src/utils/theme-native.js'), /static\/images/);
  assert.equal(existsSync(new URL('../src/static/images/tabbar', import.meta.url)), false);
});

test('success and incoming funds retain semantic colors', () => {
  assert.match(read('../src/pages-sub/checkout/success.vue'), /:color="COLOR_SUCCESS"/);
  assert.match(read('../src/pages-sub/wallet/index.vue'), /wallet__change--in\s*\{\s*color: \$color-success/);
  assert.match(read('../src/pages-sub/wallet/movements.vue'), /movements__amount--in\s*\{\s*color: \$color-success/);
});

test('anonymous theme failure is silent and cannot clear the customer session', async () => {
  const effects = [];
  let headers;
  let token = 'existing-customer-token';
  let defer = false;
  let respond;
  const source = read('../src/lib/xsy-request.js')
    .replace(/^[^\S\r\n]*import[\s\S]*?;\r?\n/gm, '')
    .replace('import.meta.env.VITE_APP_API_URL', "'http://example.invalid'")
    .replace(/export (const|function|class) /g, '$1 ');
  const context = vm.createContext({
    USER_TOKEN: 'token',
    SUCCESS_CODE: 0,
    SESSION_EXPIRED_CODES: [30007],
    PLATFORM_ERROR_CODE: { LOGIN_STATE_INVALID: 30007 },
    USE_MOCK: false,
    getCurrentPages: () => [],
    setTimeout: (fn) => fn(),
    uni: {
      getStorageSync: () => token,
      removeStorageSync: () => {
        token = '';
        effects.push('logout');
      },
      showToast: () => effects.push('toast'),
      reLaunch: ({ complete }) => {
        effects.push('redirect');
        complete?.();
      },
      request: (options) => {
        headers = options.header;
        respond = () => options.success({ statusCode: 401, data: { code: 30007, msg: 'unavailable' } });
        if (!defer) respond();
      },
    },
  });
  const guard = read('../src/lib/login-guard.js')
    .replace(/^[^\S\r\n]*import[\s\S]*?;\r?\n/gm, '')
    .replace(/export function /g, 'function ');
  vm.runInContext(`${guard}\n${source}\nglobalThis.get = getRequest;`, context);
  await assert.rejects(context.get('/scm/mall/home/theme', {}, { anonymous: true, silent: true }));
  assert.equal(headers.Authorization, undefined);
  assert.deepEqual(effects, []);
  await assert.rejects(context.get('/scm/mall/orders'));
  assert.equal(headers.Authorization, 'Bearer existing-customer-token');
  assert.deepEqual(effects, ['logout', 'toast', 'redirect']);

  defer = true;
  token = 'old-token';
  const oldRequest = context.get('/scm/mall/auth/profile');
  token = 'new-token';
  respond();
  await assert.rejects(oldRequest);
  assert.equal(token, 'new-token');
  assert.deepEqual(effects, ['logout', 'toast', 'redirect']);
});
