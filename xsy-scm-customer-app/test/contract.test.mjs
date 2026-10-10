import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');

test('SmartAdmin success contract uses code zero', () => {
  const constants = read('../src/constants/error-code-const.js');
  const request = read('../src/lib/smart-request.js');
  assert.match(constants, /SUCCESS_CODE = 0/);
  assert.match(request, /code === SUCCESS_CODE && res\?\.ok !== false/);
});

test('release only exposes password authentication and catalog APIs', () => {
  const auth = read('../src/api/mall/auth-api.js');
  const api = read('../src/api/mall/index.js');
  assert.match(auth, /auth\/login/);
  assert.match(auth, /password\/change/);
  assert.doesNotMatch(auth, /sms|wechat/i);
  assert.doesNotMatch(api, /Cart|Checkout|Order|Address|Account|Home/);
});

test('login and password forms match the backend payload', () => {
  const store = read('../src/store/modules/system/user.js');
  const password = read('../src/pages/account/password.vue');
  assert.match(store, /const profile = data\.profile \|\| data/);
  assert.match(store, /profile\.loginPhone/);
  assert.match(store, /profile\.firstPasswordChange/);
  assert.match(password, /oldPassword/);
  assert.doesNotMatch(password, /currentPassword/);
});

test('cart actions report unavailable instead of fake success', () => {
  for (const page of ['../src/pages/home/index.vue', '../src/pages/category/index.vue', '../src/pages-sub/product/search.vue', '../src/pages-sub/product/detail.vue']) {
    const source = read(page);
    assert.match(source, /购物车.*下一阶段开放|购物车暂未开放/);
    assert.doesNotMatch(source, /加入购物车：/);
  }
});

test('production mock alias points at noop implementation', () => {
  const vite = read('../vite.config.js');
  assert.match(vite, /useMock \? 'mock\/index\.js' : 'mock\/noop\.js'/);
});

test('frequent products follow the backend array contract', () => {
  const api = read('../src/api/mall/catalog-api.js');
  const home = read('../src/pages/home/index.vue');
  const mock = read('../mock/routes/catalog.js');
  // 后端 /scm/mall/catalog/frequent 返回 MallProductVO 数组，参数是 days / limit
  assert.match(api, /\/scm\/mall\/catalog\/frequent/);
  assert.doesNotMatch(home, /getFavorites\(\{\s*pageNum/);
  assert.match(home, /getFavorites\(\{\s*days/);
  assert.doesNotMatch(home, /response\.data\?\.list/);
  // mock 与后端一致：返回数组而不是分页包装
  assert.match(mock, /MOCK_PRODUCTS\.filter\(\(p\) => p\.favorite\)\.slice\(0, limit\)/);
});
