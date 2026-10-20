import assert from 'node:assert/strict';
import {readFileSync, existsSync} from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
import {createPinia, defineStore, setActivePinia} from 'pinia';

const read = path => readFileSync(new URL(path, import.meta.url), 'utf8');
const colors = await import(`data:text/javascript;base64,${Buffer.from(read('../src/constants/theme-color-const.js')).toString('base64')}`);

function createStore(fetchTheme) {
  setActivePinia(createPinia());
  const native = [];
  const source = read('../src/store/modules/mall/theme.js').replace(/^import .*;\r?\n/gm, '')
      .replace('export const useMallThemeStore', 'globalThis.useMallThemeStore');
  const context = vm.createContext({defineStore, ...colors, mallHomeApi: {theme: fetchTheme},
    smartSentry: {captureError() {}}, applyNativeTheme: code => native.push(code)});
  vm.runInContext(source, context);
  return {store: context.useMallThemeStore(), native};
}

test('theme follows published releases including rollback and fails closed to FRESH', async () => {
  let data = {releaseId: 1, themeCode: 'WARM'};
  const {store} = createStore(async () => ({data}));
  assert.equal(store.themeCode, 'FRESH');
  for (const [releaseId, themeCode] of [[1, 'WARM'], [2, 'OCEAN'], [3, 'WARM']]) {
    data = {releaseId, themeCode};
    await store.refresh();
    assert.equal(store.themeCode, themeCode);
    assert.equal(store.releaseId, releaseId);
    assert.equal(store.themeStyle['--mall-primary'], colors.MALL_THEMES[themeCode].primary);
  }
  for (const value of [{releaseId: 4, themeCode: 'UNKNOWN'}, {releaseId: 5, themeCode: null},
    {releaseId: null, themeCode: 'WARM'}, {}]) {
    data = value;
    await store.refresh();
    assert.equal(store.themeCode, 'FRESH');
  }
});

test('parallel page refreshes share one request; failed refresh clears a prior theme', async () => {
  let finish;
  let calls = 0;
  const {store} = createStore(() => {calls++; return new Promise((resolve, reject) => {finish = {resolve, reject};});});
  const first = store.refresh();
  const second = store.refresh();
  assert.equal(calls, 1);
  finish.resolve({data: {releaseId: 1, themeCode: 'OCEAN'}});
  await Promise.all([first, second]);
  const failed = store.refresh();
  finish.reject(new Error('offline'));
  await failed;
  assert.equal(store.themeCode, 'FRESH');
  assert.equal(store.releaseId, null);
});

test('obsolete response cannot overwrite newer state', async () => {
  let finish;
  const {store} = createStore(() => new Promise(resolve => {finish = resolve;}));
  const request = store.refresh();
  store.requestSeq++;
  store.applyTheme({releaseId: 2, themeCode: 'OCEAN'});
  finish({data: {releaseId: 1, themeCode: 'WARM'}});
  await request;
  assert.equal(store.themeCode, 'OCEAN');
});

test('every registered page participates in theme lifecycle, including direct subpackage routes', () => {
  const config = JSON.parse(read('../src/pages.json'));
  const routes = [...config.pages.map(page => page.path),
    ...config.subPackages.flatMap(pkg => pkg.pages.map(page => `${pkg.root}/${page.path}`))];
  assert.equal(routes.length, 27);
  for (const route of routes) {
    const source = read(`../src/${route}.vue`);
    assert.match(source, /useMallTheme\(\)/, route);
    assert.match(source, /:style="themeStyle"/, route);
  }
  assert.match(read('../src/composables/use-mall-theme.js'), /onShow\(/);
  assert.match(read('../src/App.vue'), /onShow:/);
  assert.doesNotMatch(read('../src/pages/home/index.vue'), /themeCode\.value\s*=/);
});

test('native tab colors retry after navigation and all selected icons exist', () => {
  const calls = [];
  const source = read('../src/utils/theme-native.js').replace(/^import .*;\r?\n/gm, '')
      .replace('export function applyNativeTheme', 'function applyNativeTheme');
  const context = vm.createContext({...colors, uni: {
    setTabBarStyle: options => calls.push(options), setTabBarItem: options => calls.push(options),
  }});
  vm.runInContext(source, context);
  context.applyNativeTheme('WARM');
  context.applyNativeTheme('WARM');
  assert.equal(calls.length, 12);
  for (const theme of ['FRESH', 'WARM', 'OCEAN']) {
    for (const icon of ['home', 'category', 'cart', 'order', 'mine']) {
      const suffix = theme === 'FRESH' ? '' : `-${theme.toLowerCase()}`;
      assert.ok(existsSync(new URL(`../src/static/images/tabbar/${icon}-h${suffix}.png`, import.meta.url)));
    }
  }
});

test('success and incoming funds retain semantic colors', () => {
  assert.match(read('../src/pages-sub/checkout/success.vue'), /:color="COLOR_SUCCESS"/);
  assert.match(read('../src/pages-sub/wallet/index.vue'), /wallet__change--in\s*\{\s*color: \$color-success/);
  assert.match(read('../src/pages-sub/wallet/movements.vue'), /movements__amount--in\s*\{\s*color: \$color-success/);
});

test('anonymous theme failure is silent and cannot clear the customer session', async () => {
  const effects = [];
  let headers;
  const source = read('../src/lib/smart-request.js').replace(/^import .*;\r?\n/gm, '')
      .replace('import.meta.env.VITE_APP_API_URL', "'http://example.invalid'")
      .replace(/export (const|function|class) /g, '$1 ');
  const context = vm.createContext({USER_TOKEN: 'token', SUCCESS_CODE: 0, SESSION_EXPIRED_CODES: [30007],
    PLATFORM_ERROR_CODE: {LOGIN_STATE_INVALID: 30007}, USE_MOCK: false,
    setTimeout: fn => fn(), uni: {
      getStorageSync: () => 'existing-customer-token',
      removeStorageSync: () => effects.push('logout'),
      showToast: () => effects.push('toast'),
      reLaunch: () => effects.push('redirect'),
      request: options => {
        headers = options.header;
        options.success({statusCode: 401, data: {code: 30007, msg: 'unavailable'}});
      },
    }});
  vm.runInContext(`${source}\nglobalThis.get = getRequest;`, context);
  await assert.rejects(context.get('/scm/mall/home/theme', {}, {anonymous: true, silent: true}));
  assert.equal(headers.Authorization, undefined);
  assert.deepEqual(effects, []);
  await assert.rejects(context.get('/scm/mall/orders'));
  assert.equal(headers.Authorization, 'Bearer existing-customer-token');
  assert.deepEqual(effects, ['logout', 'toast', 'redirect']);
});
