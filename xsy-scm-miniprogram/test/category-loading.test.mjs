import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

function harness() {
  const calls = [];
  const source = readFileSync(new URL('../src/pages/category/index.vue', import.meta.url), 'utf8');
  const start = source.indexOf('async function loadProducts(');
  const end = start + source.slice(start).search(/\n[^\S\r\n]*\/\*\*/);
  const context = vm.createContext({
    loading: { value: false },
    effectiveCategoryId: { value: 100 },
    pageNum: { value: 0 },
    products: { value: [] },
    total: { value: 0 },
    productError: { value: '' },
    PAGE_SIZE: 10,
    mallCatalogApi: { getProducts: (query) => new Promise((resolve, reject) => calls.push({ query, resolve, reject })) },
    xsySentry: { captureError() {} },
  });
  vm.runInContext(`let productRequest = 0;\n${source.slice(start, end)}\nglobalThis.load = loadProducts;`, context);
  return { state: context, calls };
}

test('switching from a slow populated category to an empty category discards old products', async () => {
  const { state, calls } = harness();
  const first = state.load(true);
  state.effectiveCategoryId.value = 200;
  const empty = state.load(true);
  assert.equal(calls.length, 2);
  assert.equal(calls[1].query.categoryId, 200);
  calls[1].resolve({ data: { list: [], total: 0 } });
  await empty;
  calls[0].resolve({ data: { list: [{ skuId: 1 }], total: 1 } });
  await first;
  assert.equal(state.products.value.length, 0);
  assert.equal(state.total.value, 0);
  assert.equal(state.loading.value, false);
});

test('failed category reads remain errors and successful empty retries clear the error', async () => {
  const { state, calls } = harness();
  const failed = state.load(true);
  calls[0].reject(new Error('网络异常'));
  await failed;
  assert.equal(state.productError.value, '网络异常');
  const retry = state.load(true);
  calls[1].resolve({ data: { list: [], total: 0 } });
  await retry;
  assert.equal(state.productError.value, '');
  assert.equal(state.products.value.length, 0);
});

test('third-level empty categories can be selected and linked without hiding siblings', async () => {
  const source = readFileSync(new URL('../src/pages/category/index.vue', import.meta.url), 'utf8')
    .match(/<script setup>([\s\S]*?)<\/script>/)[1]
    .replace(/^[^\S\r\n]*import[\s\S]*?;\r?\n/gm, '');
  const requests = [];
  const navigation = {
    pendingCategoryId: null,
    clearPendingCategoryId() {
      this.pendingCategoryId = null;
    },
  };
  const tree = [
    {
      categoryId: 1,
      categoryName: '水果',
      children: [
        {
          categoryId: 2,
          categoryName: '国产水果',
          children: [
            { categoryId: 3, categoryName: '苹果' },
            { categoryId: 4, categoryName: '空柑橘' },
          ],
        },
        { categoryId: 5, categoryName: '进口水果', children: [] },
      ],
    },
  ];
  const context = vm.createContext({
    ref: (value) => ({ value }),
    computed: (fn) => ({
      get value() {
        return fn();
      },
    }),
    onShow() {},
    useMallTheme: () => ({}),
    useMallNavigationStore: () => navigation,
    useMallCart: () => ({}),
    mallCatalogApi: {
      getCategories: async () => ({ data: tree }),
      getProducts: async (q) => {
        requests.push(q);
        return { data: { list: [], total: 0 } };
      },
    },
    xsySentry: { captureError() {} },
  });
  vm.runInContext(
    source +
      '\nglobalThis.api={selectSub,selectLeaf,selectAllSubs,applyPendingCategory,subCategories,leafCategories,activeLeafId,effectiveCategoryId,products};',
    context
  );
  await new Promise((resolve) => setImmediate(resolve));
  const api = context.api;
  api.selectSub(tree[0].children[0]);
  api.selectLeaf(tree[0].children[0].children[1]);
  assert.equal(requests.at(-1).categoryId, 4);
  assert.equal(api.subCategories.value.length, 2);
  assert.equal(api.leafCategories.value.length, 2);
  assert.equal(api.products.value.length, 0);
  api.selectLeaf(null);
  assert.equal(requests.at(-1).categoryId, 2);
  navigation.pendingCategoryId = 3;
  api.applyPendingCategory();
  assert.equal(api.effectiveCategoryId.value, 3);
  assert.equal(navigation.pendingCategoryId, null);
  api.selectAllSubs();
  assert.equal(requests.at(-1).categoryId, 1);
  assert.equal(api.activeLeafId.value, null);
});
