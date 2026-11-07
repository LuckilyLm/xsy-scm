import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
import { computed, reactive, ref } from 'vue';

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');
const strip = (source) => source.replace(/^[^\S\r\n]*import[\s\S]*?;\r?\n/gm, '').replace(/\bexport /g, '');

function harness() {
  const calls = { requests: [], toasts: [], routes: [], errors: [], timers: [], clears: 0 };
  let token = 'current-session';
  const store = {
    passwordChangeRequired: false,
    clearUserLoginInfo() {
      calls.clears++;
      token = '';
    },
  };
  const context = vm.createContext({
    computed,
    reactive,
    ref,
    USE_MOCK: false,
    useMallTheme: () => ({}),
    useUserStore: () => store,
    USER_TOKEN: 'token',
    redirectToLogin: () => calls.routes.push('/pages/login/login'),
    xsySentry: { captureError: (error) => calls.errors.push(error) },
    setTimeout: (callback) => calls.timers.push(callback),
    uni: {
      getStorageSync: () => token,
      removeStorageSync: () => {
        token = '';
      },
      request: (request) => calls.requests.push(request),
      showToast: ({ title }) => calls.toasts.push(title),
      reLaunch: ({ url }) => calls.routes.push(url),
    },
  });
  // 页面、API适配器与请求层都执行真实源码，确保错误提示责任没有被测试替换。
  vm.runInContext(strip(read('../src/constants/error-code-const.js')), context);
  vm.runInContext(strip(read('../src/lib/xsy-request.js')).replace('import.meta.env.VITE_APP_API_URL', "'https://api.test'"), context);
  vm.runInContext(strip(read('../src/api/mall/auth-api.js')), context);
  const script = read('../src/pages/account/password.vue').match(/<script setup>([\s\S]*?)<\/script>/)[1];
  vm.runInContext(strip(script.trimStart()) + '\nglobalThis.page = {save, form, saving};', context);
  Object.assign(context.page.form, { oldPassword: 'old-password', newPassword: 'new-password', confirmPassword: 'new-password' });
  return { page: context.page, calls, context };
}

test('equal passwords are rejected before any request and retain the session', async () => {
  const h = harness();
  Object.assign(h.page.form, { newPassword: 'old-password', confirmPassword: 'old-password' });
  await h.page.save();
  assert.equal(h.calls.requests.length, 0);
  assert.deepEqual(h.calls.toasts, ['新密码不能与当前密码相同']);
  assert.equal(h.calls.clears, 0);
  assert.equal(h.page.saving.value, false);
});

test('incomplete, short and mismatched passwords never reach the server', async () => {
  for (const fields of [{ oldPassword: '' }, { newPassword: 'short' }, { confirmPassword: 'different-password' }]) {
    const h = harness();
    Object.assign(h.page.form, fields);
    await h.page.save();
    assert.equal(h.calls.requests.length, 0);
    assert.equal(h.calls.toasts.length, 1);
    assert.equal(h.page.saving.value, false);
  }
});

test('server rejection is shown once and failed submission can be retried', async () => {
  for (const [code, msg] of [
    [40099, '新密码不能与当前密码相同'],
    [40080, '手机号或密码错误'],
    [42980, '登录失败次数过多，账号已暂时锁定，请稍后重试'],
  ]) {
    const h = harness();
    const pending = h.page.save();
    h.calls.requests[0].success({ statusCode: 200, data: { code, ok: false, msg } });
    await pending;
    assert.deepEqual(h.calls.toasts, [msg]);
    assert.equal(h.calls.clears, 0);
    assert.equal(h.page.saving.value, false);
    const retry = h.page.save();
    assert.equal(h.calls.requests.length, 2);
    h.calls.requests[1].success({ statusCode: 200, data: { code: 0, ok: true } });
    await retry;
    assert.equal(h.calls.clears, 1);
  }
});

test('network failure produces one useful message and allows retry', async () => {
  const h = harness();
  const pending = h.page.save();
  h.calls.requests[0].fail({ errMsg: 'request:fail timeout' });
  await pending;
  assert.deepEqual(h.calls.toasts, ['网络连接失败，请检查网络后重试']);
  assert.equal(h.calls.clears, 0);
  assert.equal(h.page.saving.value, false);
  const retry = h.page.save();
  assert.equal(h.calls.requests.length, 2);
  h.calls.requests[1].success({ statusCode: 200, data: { code: 0, ok: true } });
  await retry;
  assert.equal(h.calls.clears, 1);
});

test('unexpected errors use the page fallback without clearing the session', async () => {
  for (const message of ['服务暂时不可用', '']) {
    const h = harness();
    h.context.failureMessage = message;
    vm.runInContext('mallAuthApi.changePassword = () => { throw new Error(failureMessage); };', h.context);
    await h.page.save();
    assert.deepEqual(h.calls.toasts, [message || '修改失败，请稍后重试']);
    assert.equal(h.calls.clears, 0);
    assert.equal(h.page.saving.value, false);
  }
});

test('repeated submit makes one request and success clears the session before login', async () => {
  const h = harness();
  const pending = h.page.save();
  await h.page.save();
  assert.equal(h.calls.requests.length, 1);
  assert.equal(h.page.saving.value, true);
  const request = h.calls.requests[0];
  assert.equal(request.url, 'https://api.test/scm/mall/auth/password/change');
  assert.equal(request.header.Authorization, 'Bearer current-session');
  assert.equal(request.data.oldPassword, 'old-password');
  assert.equal(request.data.newPassword, 'new-password');
  assert.equal(request.data.confirmPassword, undefined);
  request.success({ statusCode: 200, data: { code: 0, ok: true } });
  await pending;
  assert.equal(h.calls.clears, 1);
  assert.deepEqual(h.calls.toasts, ['密码已修改，请重新登录']);
  assert.deepEqual(h.calls.routes, []);
  h.calls.timers.forEach((callback) => callback());
  assert.deepEqual(h.calls.routes, ['/pages/login/login']);
  assert.equal(h.page.saving.value, false);
});
