import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
import {computed, ref, reactive} from 'vue';

const source = readFileSync(new URL('../src/pages/login/login.vue', import.meta.url), 'utf8');

function harness(login) {
  const calls = {phone: [], password: [], saved: [], routes: [], toasts: [], errors: [], loading: 0};
  const store = {
    passwordChangeRequired: false,
    setUserLoginInfo(data) {
      calls.saved.push(data);
      this.passwordChangeRequired = !!data.profile.firstPasswordChange;
    },
  };
  const context = vm.createContext({
    computed,
    ref,
    reactive,
    useMallTheme: () => ({}),
    useMallStorefront: () => ({storeName: ref('商城')}),
    useUserStore: () => store,
    mallAuthApi: {
      wechatPhoneLogin: (form) => {
        calls.phone.push(form);
        return login(form);
      },
      login: (form) => {
        calls.password.push(form);
        return login(form);
      },
    },
    smartSentry: {captureError: (error) => calls.errors.push(error)},
    uni: {
      showToast: ({title}) => calls.toasts.push(title),
      showLoading: () => calls.loading++,
      hideLoading: () => calls.loading--,
      switchTab: ({url}) => calls.routes.push(url),
      redirectTo: ({url}) => calls.routes.push(url),
    },
  });
  const script = source
    .match(/<script setup>([\s\S]*?)<\/script>/)[1]
    .trimStart()
    .replace(/^import[\s\S]*?;\r?\n/gm, '');
  vm.runInContext(script + '\nglobalThis.api = {wechatLogin, submit, form, submitting, loginTitle};', context);
  return {page: context.api, calls};
}

test('wechat authorization denial or missing code never sends a login request', async () => {
  const {page, calls} = harness(() => {
    throw new Error('must not request');
  });
  await page.wechatLogin({detail: {errMsg: 'getPhoneNumber:fail user deny'}});
  await page.wechatLogin({detail: {errMsg: 'getPhoneNumber:ok'}});
  assert.equal(calls.phone.length, 0);
  assert.equal(calls.saved.length, 0);
  assert.equal(calls.routes.length, 0);
  assert.equal(calls.toasts.length, 2);
});

test('verified phone signs in with only the one-time code and coalesces repeated authorization events', async () => {
  let resolve;
  const {page, calls} = harness(
    () =>
      new Promise((done) => {
        resolve = done;
      })
  );
  const event = {detail: {errMsg: 'getPhoneNumber:ok', code: 'one-time-code', phoneNumber: 'untrusted-phone'}};
  const first = page.wechatLogin(event);
  await page.wechatLogin(event);
  assert.equal(calls.phone.length, 1);
  assert.equal(JSON.stringify(calls.phone[0]), JSON.stringify({code: 'one-time-code'}));
  assert.equal(page.submitting.value, true);
  resolve({data: {token: 'test-token', profile: {firstPasswordChange: false}}});
  await first;
  assert.deepEqual(calls.routes, ['/pages/home/index']);
  assert.equal(calls.saved.length, 1);
  assert.equal(page.submitting.value, false);
  assert.equal(calls.loading, 0);
});

test('failed authorization exchange leaves user signed out and allows a new authorization', async () => {
  let fail = true;
  const {page, calls} = harness(async () => {
    if (fail) throw new Error('expired authorization');
    return {data: {token: 'new-token', profile: {firstPasswordChange: false}}};
  });
  await page.wechatLogin({detail: {errMsg: 'getPhoneNumber:ok', code: 'expired'}});
  assert.equal(calls.saved.length, 0);
  assert.equal(calls.routes.length, 0);
  assert.equal(page.submitting.value, false);
  fail = false;
  await page.wechatLogin({detail: {errMsg: 'getPhoneNumber:ok', code: 'new-authorization'}});
  assert.equal(calls.saved.length, 1);
  assert.equal(calls.loading, 0);
});

test('password fallback still obeys the server temporary-password requirement', async () => {
  const {page, calls} = harness(async () => ({data: {token: 'password-token', profile: {firstPasswordChange: true}}}));
  page.form.phone = '13800000000';
  page.form.password = 'temporary-password';
  await page.submit();
  assert.equal(calls.password.length, 1);
  assert.equal(calls.phone.length, 0);
  assert.deepEqual(calls.routes, ['/pages/account/password']);
  assert.equal(calls.loading, 0);
});
