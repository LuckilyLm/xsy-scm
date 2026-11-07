import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
import { computed, ref } from 'vue';

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');
const withoutImports = (source) => source.replace(/^[^\S\r\n]*import[\s\S]*?;\r?\n/gm, '');

function harness(initialToken = '') {
  let token = initialToken;
  let route = 'pages/home/index';
  const calls = { toasts: [], routes: [], requests: [], timers: [], interceptors: {} };
  const app = {
    use() {},
    mixin(value) {
      this.hooks = value;
    },
  };
  const context = vm.createContext({
    USER_TOKEN: 'token',
    PLATFORM_ERROR_CODE: { LOGIN_STATE_INVALID: 30007 },
    SESSION_EXPIRED_CODES: [30007, 30008, 30012],
    SUCCESS_CODE: 0,
    USE_MOCK: false,
    store: {},
    createSSRApp: () => app,
    App: {},
    useMallThemeStore: () => ({ refresh() {} }),
    useUserStore: () => ({ getLoginInfo() {} }),
    getCurrentPages: () => [{ route }],
    setTimeout: (callback) => calls.timers.push(callback),
    uni: {
      getStorageSync: () => token,
      removeStorageSync: () => (token = ''),
      showToast: ({ title }) => calls.toasts.push(title),
      addInterceptor: (method, interceptor) => (calls.interceptors[method] = interceptor),
      reLaunch: ({ url, complete }) => {
        calls.routes.push(url);
        route = url.slice(1);
        complete?.();
      },
      request: (options) => calls.requests.push(options),
    },
  });
  const source = [
    read('../src/lib/login-guard.js'),
    read('../src/lib/xsy-request.js').replace('import.meta.env.VITE_APP_API_URL', "'https://test.example/api'"),
    read('../src/main.js'),
  ]
    .map((part) => withoutImports(part).replace(/\bexport /g, ''))
    .join('\n');
  vm.runInContext(source + '\nglobalThis.api = { guardPage, guardCurrentPage, installLoginGuard, getRequest, createApp };', context);
  const appScript = read('../src/App.vue').match(/<script>([\s\S]*?)<\/script>/)[1];
  vm.runInContext(withoutImports(appScript.trimStart()).replace('export default', 'globalThis.lifecycle ='), context);
  return {
    api: context.api,
    context,
    calls,
    app,
    lifecycle: context.lifecycle,
    setToken: (value) => (token = value),
    setRoute: (value) => (route = value),
    flush: () => {
      while (calls.timers.length) calls.timers.shift()();
    },
  };
}

test('all navigation APIs block guest business pages and merge concurrent login redirects', () => {
  const h = harness();
  h.api.createApp();
  h.api.installLoginGuard();
  for (const method of ['navigateTo', 'redirectTo', 'reLaunch', 'switchTab']) {
    assert.equal(h.calls.interceptors[method].invoke({ url: '/pages/order/index?status=all' }), false);
  }
  assert.deepEqual(h.calls.toasts, ['请先登录']);
  assert.equal(h.calls.routes.length, 0);
  h.flush();
  assert.deepEqual(h.calls.routes, ['/pages/login/login']);
});

test('public pages remain open while every other registered page requires login', () => {
  const h = harness();
  const pages = JSON.parse(read('../src/pages.json'));
  const publicPages = new Set(['pages/home/index', 'pages/mine/index', 'pages/login/login', 'pages-sub/page/index', 'pages-sub/trace/detail']);
  const paths = pages.pages
    .map((page) => page.path)
    .concat(pages.subPackages.flatMap((group) => group.pages.map((page) => `${group.root}/${page.path}`)));
  for (const path of paths) assert.equal(h.api.guardPage('/' + path), publicPages.has(path), path);
  assert.equal(h.api.guardPage(undefined), true);
});

test('page lifecycle catches direct opens and returning to cached protected pages', () => {
  const h = harness();
  h.api.createApp();
  h.setRoute('pages-sub/checkout/success');
  assert.equal(h.app.hooks.onShow(), false);
  h.flush();
  assert.deepEqual(h.calls.routes, ['/pages/login/login']);
  h.setRoute('pages/cart/index');
  assert.equal(h.app.hooks.onShow(), false);
  h.flush();
  assert.equal(h.calls.routes.length, 2);
});

test('guest cold start toasts and enters login immediately after the first page is ready', () => {
  for (const route of ['pages/home/index', 'pages/mine/index']) {
    const h = harness();
    h.api.createApp();
    h.setRoute(undefined);
    assert.equal(h.app.hooks.onShow(), true);
    assert.deepEqual(h.calls.routes, []);
    assert.deepEqual(h.calls.toasts, []);
    h.setRoute(route);
    assert.equal(h.app.hooks.onShow(), false);
    assert.deepEqual(h.calls.routes, ['/pages/login/login']);
    assert.equal(h.calls.timers.length, 0);
    assert.deepEqual(h.calls.toasts, ['请先登录']);
    h.app.hooks.onShow();
    assert.equal(h.calls.routes.length, 1);
  }
});

test('cold start preserves cached sessions and direct public information links', () => {
  const loggedIn = harness('cached-token');
  loggedIn.api.createApp();
  assert.equal(loggedIn.app.hooks.onShow(), true);
  assert.deepEqual(loggedIn.calls.routes, []);
  for (const route of ['pages/login/login', 'pages-sub/page/index', 'pages-sub/trace/detail']) {
    const h = harness();
    h.api.createApp();
    h.setRoute(route);
    assert.equal(h.app.hooks.onShow(), true);
    assert.deepEqual(h.calls.routes, []);
  }
});

test('reopening the app checks login again even when WeChat reuses the background page', () => {
  const h = harness('cached-token');
  h.api.createApp();
  h.app.hooks.onShow();
  assert.deepEqual(h.calls.routes, []);
  h.setToken('');
  h.lifecycle.onShow();
  assert.deepEqual(h.calls.routes, ['/pages/login/login']);
  assert.equal(h.calls.timers.length, 0);
});

test('cached token allows navigation before profile restoration and cancels a pending guest redirect', () => {
  const h = harness('cached-token');
  assert.equal(h.api.guardPage('/pages/category/index'), true);
  assert.equal(h.calls.toasts.length, 0);
  h.setToken('');
  h.api.guardPage('/pages/order/index');
  h.setToken('new-token');
  h.flush();
  assert.deepEqual(h.calls.routes, []);
});

test('guest protected requests fail locally without a network request or a network error', async () => {
  const h = harness();
  for (const path of ['/scm/mall/orders', '/scm/mall/cart', '/scm/mall/catalog/products']) {
    await assert.rejects(h.api.getRequest(path), (error) => error.code === 30007 && error.message === '请先登录');
  }
  assert.equal(h.calls.requests.length, 0);
  assert.deepEqual(h.calls.toasts, ['请先登录']);
  h.flush();
  assert.deepEqual(h.calls.routes, ['/pages/login/login']);
});

test('public requests and login remain available, and authenticated requests carry the cached token', async () => {
  const h = harness();
  for (const [path, options] of [
    ['/scm/mall/home/theme', { anonymous: true }],
    ['/scm/mall/auth/login', {}],
  ]) {
    const pending = h.api.getRequest(path, {}, options);
    const request = h.calls.requests.at(-1);
    assert.equal(request.header.Authorization, undefined);
    request.success({ statusCode: 200, data: { code: 0, ok: true } });
    await pending;
  }
  h.setToken('cached-token');
  const pending = h.api.getRequest('/scm/mall/orders');
  assert.equal(h.calls.requests.at(-1).header.Authorization, 'Bearer cached-token');
  h.calls.requests.at(-1).success({ statusCode: 200, data: { code: 0, ok: true } });
  await pending;
  // 标签令牌是溯源凭据，不能误用客户会话；执行真实 API 适配器检查其请求选项。
  vm.runInContext(
    withoutImports(read('../src/pages-sub/shared/api/trace/trace-public-api.js'))
      .replace(/const baseUrl = .*?;/, '')
      .replace('export const tracePublicApi', 'globalThis.tracePublicApi'),
    h.context
  );
  const trace = h.context.tracePublicApi.detail('label-token');
  assert.equal(h.calls.requests.at(-1).header.Authorization, undefined);
  h.calls.requests.at(-1).success({ statusCode: 200, data: { code: 0, ok: true } });
  await trace;
  assert.deepEqual(h.calls.toasts, []);
});

test('stale session errors do not log out a newer session; current expiry shares the login redirect', async () => {
  const h = harness('old-token');
  const old = h.api.getRequest('/scm/mall/orders');
  h.setToken('new-token');
  h.calls.requests.at(-1).success({ statusCode: 200, data: { code: 30007, msg: '登录已失效' } });
  await assert.rejects(old);
  assert.deepEqual(h.calls.toasts, []);
  const current = h.api.getRequest('/scm/mall/orders');
  h.calls.requests.at(-1).success({ statusCode: 200, data: { code: 30007, msg: '登录已失效' } });
  await assert.rejects(current);
  h.api.guardPage('/pages/cart/index');
  assert.deepEqual(h.calls.toasts, ['登录已失效，请重新登录']);
  h.flush();
  assert.deepEqual(h.calls.routes, ['/pages/login/login']);
});

test('guest homepage sends no customer requests and loads data after profile restoration', async () => {
  const calls = [];
  const watchers = [];
  const user = { isLogin: false, customerId: null };
  const response = (name, data) => async () => {
    calls.push(name);
    return { data };
  };
  const context = vm.createContext({
    computed,
    ref,
    watch: (_, callback) => watchers.push(callback),
    onHide() {},
    onPageScroll() {},
    onReachBottom() {},
    onShow() {},
    useMallTheme: () => ({}),
    useMallStorefront: () => ({}),
    useMallCart: () => ({}),
    useSystemLayout: () => ({ statusBarHeight: ref(24), menuButtonRect: ref(null) }),
    useUserStore: () => user,
    useMallNavigationStore: () => ({}),
    mallCatalogApi: {
      getProducts: response('products', { list: [], total: 0 }),
      getCategories: response('categories', []),
      getFavorites: response('favorites', []),
    },
    mallAddressApi: { getList: response('addresses', []) },
    mallHomeApi: { published: response('layout', {}), popup: response('popup', {}) },
    mallPromotionApi: { activities: response('activities', []), newProducts: response('newProducts', []) },
    xsySentry: {
      captureError(error) {
        throw error;
      },
    },
  });
  const script = read('../src/pages/home/index.vue').match(/<script setup>([\s\S]*?)<\/script>/)[1];
  vm.runInContext(withoutImports(script.trimStart()), context);
  await Promise.resolve();
  assert.deepEqual(calls, []);
  Object.assign(user, { isLogin: true, customerId: 1 });
  watchers[0]();
  await Promise.resolve();
  await Promise.resolve();
  for (const name of ['products', 'categories', 'addresses', 'layout', 'popup']) assert.ok(calls.includes(name), name);
});
