import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
import { computed, ref, watch, nextTick, effectScope } from 'vue';
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc';
import { createPinia, defineStore, setActivePinia } from 'pinia';
import { withMedia } from './mall-media-loader.mjs';

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');
const colors = await import(`data:text/javascript;base64,${Buffer.from(read('../src/constants/theme-color-const.js')).toString('base64')}`);

test('home header uses a green-white default and follows published custom colors and rollback', async () => {
  let data = { releaseId: 1, themeCode: 'FRESH' };
  const { store } = createStore(async () => ({ data }));
  assert.equal(store.headerGradient.start, '#E8FAEC');
  assert.equal(store.headerGradient.end, '#FFFFFF');
  assert.equal(store.headerGradient.foreground, '#1F2329');
  data = { releaseId: 2, themeCode: 'FRESH', headerStartColor: '#e4f1ff', headerEndColor: '#ffffff' };
  await store.refresh();
  assert.equal(store.headerGradient.start, '#E4F1FF');
  assert.equal(store.headerGradient.end, '#FFFFFF');
  assert.equal(store.theme.primary, colors.MALL_THEMES.FRESH.primary);
  data = { releaseId: 3, themeCode: 'FRESH', headerStartColor: '#142d26', headerEndColor: '#24392f' };
  await store.refresh();
  assert.equal(store.headerGradient.foreground, '#FFFFFF');
  data = { releaseId: 4, themeCode: 'FRESH' };
  await store.refresh();
  assert.equal(store.headerGradient.start, '#E8FAEC');
  store.applyTheme({ releaseId: null, headerStartColor: '#142d26', headerEndColor: 'invalid' });
  assert.equal(store.headerGradient.start, '#E8FAEC');
  assert.equal(store.headerGradient.end, '#FFFFFF');
});
const visuals = await import(
  `data:text/javascript;base64,${Buffer.from(withMedia(read('../src/constants/mall-visual-const.js'))).toString('base64')}`
);

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
  assert.equal(routes.length, 30);
  for (const route of routes) {
    const source = read(`../src/${route}.vue`);
    assert.match(source, /useMallTheme\(/, route);
    assert.match(source, /:style="themeStyle"/, route);
  }
  assert.match(read('../src/composables/use-mall-theme.js'), /onShow\(/);
  assert.match(read('../src/App.vue'), /onShow:/);
  assert.doesNotMatch(read('../src/pages/home/index.vue'), /themeCode\.value\s*=/);
});

test('shared header follows theme and scroll only while its page is visible', async () => {
  const { store } = createStore(async () => ({ data: { releaseId: 1, themeCode: 'FRESH' } }));
  const calls = [],
    shows = [],
    hides = [];
  const source = read('../src/composables/use-mall-theme.js')
    .replace(/^[^\S\r\n]*import[\s\S]*?;\r?\n/gm, '')
    .replace('export function useMallTheme', 'function useMallTheme');
  const context = vm.createContext({
    computed,
    watch,
    useMallThemeStore: () => store,
    onShow: (fn) => shows.push(fn),
    onHide: (fn) => hides.push(fn),
    uni: { setNavigationBarColor: (x) => calls.push(x) },
  });
  vm.runInContext(source + '\nglobalThis.useTheme=useMallTheme;', context);
  const scrolled = ref(false);
  const scope = effectScope();
  scope.run(() => {
    context.useTheme({ header: true, scrolled });
    context.useTheme({ header: true });
    context.useTheme();
  });
  try {
    shows[0]();
    await store.refresh();
    await nextTick();
    assert.equal(calls.at(-1).frontColor, '#000000');
    assert.equal(store.themeStyle['--mall-header-gradient'], store.headerGradient.background);
    hides[0]();
    const count = calls.length;
    store.applyTheme({ releaseId: 2, themeCode: 'FRESH', headerStartColor: '#142D26', headerEndColor: '#24392F' });
    await nextTick();
    assert.equal(calls.length, count);
    // 我的显示时先应用当前共享配色，随后公开请求仍按同一入口刷新。
    shows[1]();
    assert.equal(calls.at(-1).frontColor, '#ffffff');
    hides[1]();
    shows[2]();
    const whitePageCalls = calls.length;
    await store.refresh();
    await nextTick();
    assert.equal(calls.length, whitePageCalls);
    shows[0]();
    await store.refresh();
    scrolled.value = true;
    await nextTick();
    assert.equal(calls.at(-1).frontColor, '#000000');
    assert.equal(calls.at(-1).backgroundColor, '#ffffff');
  } finally {
    scope.stop();
  }
});

test('mine keeps phone off the header and avatar opens the protected profile page', () => {
  const user = { isLogin: true, customerName: '测试客户', customerCode: 'CUS000001', contactName: '陈先生', phone: '13900000011' };
  const routes = [];
  const script = (path) =>
    read(path)
      .match(/<script setup>([\s\S]*?)<\/script>/)[1]
      .replace(/^[^\S\r\n]*import[\s\S]*?;\r?\n/gm, '');
  const context = vm.createContext({
    computed,
    useMallTheme: () => ({}),
    useUserStore: () => user,
    useMallStorefront: () => ({ storeName: ref('测试店铺'), storeLink: ref('') }),
    useSystemLayout: () => ({ statusBarHeight: ref(44), menuButtonRect: ref(null) }),
    MALL_SHORTCUT_IMAGES: visuals.MALL_SHORTCUT_IMAGES,
    MALL_PROFILE_IMAGES: visuals.MALL_PROFILE_IMAGES,
    uni: { navigateTo: (x) => routes.push(x.url) },
  });
  vm.runInContext(script('../src/pages/mine/index.vue') + '\nglobalThis.mine={metaLine,goProfile};', context);
  assert.equal(context.mine.metaLine.value, user.customerCode);
  assert.ok(!context.mine.metaLine.value.includes(user.phone));
  context.mine.goProfile();
  assert.deepEqual(routes, ['/pages-sub/account/profile']);
  vm.runInContext(
    script('../src/pages-sub/account/profile.vue')
      .replace('const { themeStyle }', 'const { themeStyle: profileStyle }')
      .replace('const userStore =', 'const profileUser =')
      .replaceAll('userStore.', 'profileUser.') + '\nglobalThis.profileRows=rows;',
    context
  );
  assert.equal(context.profileRows.value.find((x) => x.label === '登录手机号').value, user.phone);
  assert.match(read('../src/pages/mine/index.vue'), /class="mine__avatar"[^>]*@click\.stop="goProfile"/);
});

test('home, mine and profile templates have no undeclared render bindings', () => {
  for (const route of ['pages/home/index', 'pages/mine/index', 'pages-sub/account/profile']) {
    const { descriptor } = parse(read(`../src/${route}.vue`));
    const { bindings } = compileScript(descriptor, { id: route });
    const result = compileTemplate({
      source: descriptor.template.content,
      filename: route + '.vue',
      id: route,
      compilerOptions: { bindingMetadata: bindings },
    });
    assert.deepEqual(result.errors, [], route);
    assert.deepEqual(result.code.match(/\b_ctx\.(?!\$)[a-zA-Z_]\w*/g) || [], [], `${route}存在未声明的模板变量`);
  }
});

test('native dialogs read the latest theme and preserve explicit colors and callbacks', () => {
  const interceptors = [];
  const context = vm.createContext({
    ...colors,
    uni: { addInterceptor: (name, hooks) => interceptors.push({ name, hooks }), setTabBarStyle() {} },
  });
  const source = read('../src/utils/theme-native.js')
    .replace(/^[^\S\r\n]*import[\s\S]*?;\r?\n/gm, '')
    .replace('export function applyNativeTheme', 'function applyNativeTheme');
  vm.runInContext(source, context);
  context.applyNativeTheme(colors.MALL_THEMES.OCEAN);
  assert.equal(interceptors[0].name, 'showModal');
  const invoke = interceptors[0].hooks.invoke;
  const success = () => {};
  const options = { title: '退出登录', success };
  invoke(options);
  assert.equal(options.confirmColor, colors.MALL_THEMES.OCEAN.ink);
  assert.equal(options.success, success);
  context.applyNativeTheme(colors.MALL_THEMES.WARM);
  assert.equal(interceptors.length, 1);
  const next = {};
  invoke(next);
  assert.equal(next.confirmColor, colors.MALL_THEMES.WARM.ink);
  const danger = { confirmColor: colors.COLOR_DANGER };
  invoke(danger);
  assert.equal(danger.confirmColor, colors.COLOR_DANGER);
  context.applyNativeTheme(null);
  const fallback = {};
  invoke(fallback);
  assert.equal(fallback.confirmColor, colors.MALL_THEMES.FRESH.ink);
});

test('login surfaces share the published theme instead of fixed green scene tokens', () => {
  const login = read('../src/pages/login/login.vue');
  assert.match(login, /background:\s*var\(--mall-header-gradient\)/);
  assert.match(login, /background:\s*var\(--mall-primary-light\)/);
  assert.doesNotMatch(login, /\$color-login-(?:sky|ground|field)/);
  assert.match(read('../src/main.js'), /applyNativeTheme\(useMallThemeStore\(\)\.theme\)/);
});

test('底部导航使用交付的成对PNG，路由与原生栏隐藏仍由公共组件维护', () => {
  // 原生 TabBar 只保留颜色同步：自绘导航挂载后原生栏会被隐藏
  const calls = [];
  const source = read('../src/utils/theme-native.js')
    .replace(/^[^\S\r\n]*import[\s\S]*?;\r?\n/gm, '')
    .replace('export function applyNativeTheme', 'function applyNativeTheme');
  const context = vm.createContext({ ...colors, uni: { addInterceptor() {}, setTabBarStyle: (options) => calls.push(options) } });
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
