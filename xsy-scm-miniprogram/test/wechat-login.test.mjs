import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
import { computed, ref, reactive } from 'vue';
import { withMedia } from './mall-media-loader.mjs';

const source = readFileSync(new URL('../src/pages/login/login.vue', import.meta.url), 'utf8');

function harness(login) {
  const calls = { phone: [], password: [], saved: [], routes: [], toasts: [], loadingAtToast: [], loading: 0, globalLoadingCalls: 0 };
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
    useMallStorefront: () => ({ storeName: ref('商城') }),
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
    uni: {
      showToast: ({ title }) => {
        calls.toasts.push(title);
        calls.loadingAtToast.push(calls.loading);
      },
      showLoading: () => {
        calls.globalLoadingCalls++;
        throw new Error('global loading must not be used');
      },
      hideLoading: () => {
        calls.globalLoadingCalls++;
        throw new Error("hideLoading:fail:toast can't be found");
      },
      switchTab: ({ url }) => calls.routes.push(url),
      redirectTo: ({ url }) => calls.routes.push(url),
    },
  });
  const script = source
    .match(/<script setup>([\s\S]*?)<\/script>/)[1]
    .trimStart()
    .replace(/^[^\S\r\n]*import[\s\S]*?;\r?\n/gm, '');
  vm.runInContext(withMedia(script) + '\nglobalThis.api = {wechatLogin, submit, form, submitting, loginTitle};', context);
  return { page: context.api, calls };
}

test('wechat authorization denial or missing code never sends a login request', async () => {
  const { page, calls } = harness(() => {
    throw new Error('must not request');
  });
  await page.wechatLogin({ detail: { errMsg: 'getPhoneNumber:fail user deny' } });
  await page.wechatLogin({ detail: { errMsg: 'getPhoneNumber:ok' } });
  assert.equal(calls.phone.length, 0);
  assert.equal(calls.saved.length, 0);
  assert.equal(calls.routes.length, 0);
  assert.equal(calls.toasts.length, 1);
  assert.match(calls.toasts[0], /暂时无法获取微信手机号/);
});

test('privacy declaration and platform capability errors never masquerade as an unopened customer account', async () => {
  const { page, calls } = harness(() => {
    throw new Error('must not request');
  });
  await page.wechatLogin({ detail: { errMsg: 'getPhoneNumber:fail no permission' } });
  await page.wechatLogin({ detail: { errMsg: 'getPhoneNumber:fail api scope is not declared in the privacy agreement', errno: 112 } });
  await page.wechatLogin({ detail: { errMsg: 'getPhoneNumber:fail privacy authorization denied', errno: 104 } });
  assert.equal(calls.phone.length, 0);
  assert.deepEqual(calls.toasts, ['暂时无法使用微信手机号登录，请使用手机号密码登录', '微信手机号登录暂不可用，请联系商家处理']);
  assert.equal(calls.saved.length, 0);
});

test('unopened phone is reported only after WeChat returns a successful authorization code', async () => {
  const { page, calls } = harness(async () => {
    throw { code: 40381, msg: '该手机号尚未开通采购账号，请联系商家' };
  });
  await page.wechatLogin({ detail: { errMsg: 'getPhoneNumber:fail user deny' } });
  assert.equal(calls.phone.length, 0);
  assert.deepEqual(calls.toasts, []);
  await page.wechatLogin({ detail: { errMsg: 'getPhoneNumber:ok', code: 'authorized-phone-code' } });
  assert.equal(calls.phone.length, 1);
  assert.deepEqual(calls.toasts, ['该手机号尚未开通采购账号，请联系商家']);
  assert.equal(calls.saved.length, 0);
});

test('verified phone signs in with only the one-time code and coalesces repeated authorization events', async () => {
  let resolve;
  const { page, calls } = harness(
    () =>
      new Promise((done) => {
        resolve = done;
      })
  );
  const event = { detail: { errMsg: 'getPhoneNumber:ok', code: 'one-time-code', phoneNumber: 'untrusted-phone' } };
  const first = page.wechatLogin(event);
  await page.wechatLogin(event);
  assert.equal(calls.phone.length, 1);
  assert.equal(JSON.stringify(calls.phone[0]), JSON.stringify({ code: 'one-time-code' }));
  assert.equal(page.submitting.value, true);
  resolve({ data: { token: 'test-token', profile: { firstPasswordChange: false } } });
  await first;
  assert.deepEqual(calls.routes, ['/pages/home/index']);
  assert.equal(calls.saved.length, 1);
  assert.equal(page.submitting.value, false);
  assert.equal(calls.loading, 0);
  assert.equal(calls.globalLoadingCalls, 0);
});

test('failed authorization exchange leaves user signed out and allows a new authorization', async () => {
  let fail = true;
  const { page, calls } = harness(async () => {
    if (fail) throw { code: 40098, msg: '微信授权已失效，请重新授权手机号' };
    return { data: { token: 'new-token', profile: { firstPasswordChange: false } } };
  });
  await page.wechatLogin({ detail: { errMsg: 'getPhoneNumber:ok', code: 'expired' } });
  assert.equal(calls.saved.length, 0);
  assert.equal(calls.routes.length, 0);
  assert.equal(page.submitting.value, false);
  assert.deepEqual(calls.toasts, ['微信授权已失效，请重新授权手机号']);
  assert.deepEqual(calls.loadingAtToast, [0]);
  fail = false;
  await page.wechatLogin({ detail: { errMsg: 'getPhoneNumber:ok', code: 'new-authorization' } });
  assert.equal(calls.saved.length, 1);
  assert.equal(calls.loading, 0);
});

test('password login clears button loading before reporting a failure without using the global overlay', async () => {
  const message = '网络连接失败，请检查网络后重试';
  const { page, calls } = harness(async () => {
    throw { code: 0, msg: message };
  });
  page.form.phone = '13800000000';
  page.form.password = 'temporary-password';
  await page.submit();
  assert.deepEqual(calls.toasts, [message]);
  assert.deepEqual(calls.loadingAtToast, [0]);
  assert.equal(calls.saved.length, 0);
  assert.equal(calls.routes.length, 0);
  assert.equal(page.submitting.value, false);
  assert.equal(calls.globalLoadingCalls, 0);
});

test('password fallback still obeys the server temporary-password requirement', async () => {
  const { page, calls } = harness(async () => ({ data: { token: 'password-token', profile: { firstPasswordChange: true } } }));
  page.form.phone = '13800000000';
  page.form.password = 'temporary-password';
  await page.submit();
  assert.equal(calls.password.length, 1);
  assert.equal(calls.phone.length, 0);
  assert.deepEqual(calls.routes, ['/pages/account/password']);
  assert.equal(calls.loading, 0);
});
