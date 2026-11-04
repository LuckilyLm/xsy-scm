import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

function harness() {
  const calls = [];
  const source = readFileSync(new URL('../src/pages/category/index.vue', import.meta.url), 'utf8');
  const start = source.indexOf('async function loadProducts(');
  const end = source.indexOf('\n/**', start);
  const context = vm.createContext({
    loading: {value: false},
    effectiveCategoryId: {value: 100},
    pageNum: {value: 0},
    products: {value: []},
    total: {value: 0},
    productError: {value: ''},
    PAGE_SIZE: 10,
    mallCatalogApi: {getProducts: (query) => new Promise((resolve, reject) => calls.push({query, resolve, reject}))},
    smartSentry: {captureError() {}},
  });
  vm.runInContext(`let productRequest = 0;\n${source.slice(start, end)}\nglobalThis.load = loadProducts;`, context);
  return {state: context, calls};
}

test('switching from a slow populated category to an empty category discards old products', async () => {
  const {state, calls} = harness();
  const first = state.load(true);
  state.effectiveCategoryId.value = 200;
  const empty = state.load(true);
  assert.equal(calls.length, 2);
  assert.equal(calls[1].query.categoryId, 200);
  calls[1].resolve({data: {list: [], total: 0}});
  await empty;
  calls[0].resolve({data: {list: [{skuId: 1}], total: 1}});
  await first;
  assert.equal(state.products.value.length, 0);
  assert.equal(state.total.value, 0);
  assert.equal(state.loading.value, false);
});

test('failed category reads remain errors and successful empty retries clear the error', async () => {
  const {state, calls} = harness();
  const failed = state.load(true);
  calls[0].reject(new Error('网络异常'));
  await failed;
  assert.equal(state.productError.value, '网络异常');
  const retry = state.load(true);
  calls[1].resolve({data: {list: [], total: 0}});
  await retry;
  assert.equal(state.productError.value, '');
  assert.equal(state.products.value.length, 0);
});
