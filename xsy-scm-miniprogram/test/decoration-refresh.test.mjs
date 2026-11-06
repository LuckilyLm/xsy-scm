import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
import { ref } from 'vue';
import { createPinia, defineStore, setActivePinia } from 'pinia';

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');
const withoutImports = (source) => source.replace(/^[^\S\r\n]*import[\s\S]*?;\r?\n/gm, '');

test('saved storefront refreshes, coalesces concurrent reads and survives an offline refresh', async () => {
  setActivePinia(createPinia());
  let finish;
  let calls = 0;
  const context = vm.createContext({
    defineStore,
    mallDecorationApi: {
      store: () => {
        calls++;
        return new Promise((resolve, reject) => {
          finish = { resolve, reject };
        });
      },
    },
  });
  vm.runInContext(
    withoutImports(read('../src/store/modules/mall/storefront.js')).replace(
      'export const useMallStorefrontStore',
      'globalThis.useMallStorefrontStore'
    ),
    context
  );
  const store = context.useMallStorefrontStore();
  const first = store.refresh();
  const second = store.refresh();
  assert.equal(calls, 1);
  finish.resolve({ data: { storeName: '新店铺', storeLink: 'https://example.com/mall' } });
  await Promise.all([first, second]);
  assert.equal(store.storeName, '新店铺');
  assert.equal(store.storeLink, 'https://example.com/mall');
  const failed = store.refresh();
  finish.reject(new Error('offline'));
  await failed;
  assert.equal(store.storeName, '新店铺');
  const cleared = store.refresh();
  finish.resolve({ data: { storeName: '再更新店铺', storeLink: null } });
  await cleared;
  assert.equal(store.storeName, '再更新店铺');
  assert.equal(store.storeLink, '');
});

function pageHarness(fetchPage) {
  let onLoad, onShow;
  const titles = [];
  const context = vm.createContext({
    ref,
    computed: (get) => ({
      get value() {
        return get();
      },
    }),
    onLoad: (handler) => {
      onLoad = handler;
    },
    onShow: (handler) => {
      onShow = handler;
    },
    mallDecorationApi: { page: fetchPage },
    MALL_ERROR_CODE: { INVALID_PAGE: 41405 },
    useMallTheme: () => ({}),
    xsySentry: { captureError() {} },
    uni: { setNavigationBarTitle: ({ title }) => titles.push(title) },
  });
  const script = read('../src/pages-sub/page/index.vue').match(/<script setup>([\s\S]*?)<\/script>/)[1];
  vm.runInContext(
    `${withoutImports(
      script.trimStart()
    )}\nglobalThis.snapshot=()=>({title:title.value,content:content.value,error:error.value,loading:loading.value});`,
    context
  );
  return { open: (code) => onLoad({ pageCode: code }), show: () => onShow(), snapshot: context.snapshot, titles };
}

test('rules refresh on every page show and a late old response cannot replace a new publication', async () => {
  const pending = [];
  const page = pageHarness(() => new Promise((resolve) => pending.push(resolve)));
  page.open('AFTER_SALE');
  const initial = page.show();
  pending.shift()({ data: { title: '售后规则第一版', content: '<p>已发布正文一</p>' } });
  await initial;
  assert.equal(page.snapshot().content, '<p>已发布正文一</p>');
  const old = page.show();
  const current = page.show();
  pending[1]({ data: { title: '售后规则新版本', content: '<p>当前已发布正文</p>' } });
  await current;
  pending[0]({ data: { title: '旧请求', content: '<p>过期正文</p>' } });
  await old;
  assert.equal(page.snapshot().title, '售后规则新版本');
  assert.equal(page.snapshot().content, '<p>当前已发布正文</p>');
  assert.equal(page.titles.at(-1), '售后规则新版本');
});

test('unpublished content and network failure have different recovery messages', async () => {
  let cause = { code: 41405 };
  const page = pageHarness(async () => {
    throw cause;
  });
  page.open('TERMS');
  await page.show();
  assert.equal(page.snapshot().error, '内容暂未发布');
  cause = new Error('offline');
  await page.show();
  assert.equal(page.snapshot().error, '内容加载失败，请重试');
  assert.equal(page.snapshot().loading, false);
});
