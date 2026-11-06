import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');
const withoutImports = (source) => source.replace(/^[^\S\r\n]*import[\s\S]*?;\r?\n/gm, '');
const profile = { accountId: 1, customerId: 2, customerStatus: 'COOPERATING' };

function harness(getProfile) {
  let token = 'cached-token';
  let calls = 0;
  let onNetworkChange;
  const routes = [];
  const context = vm.createContext({
    USER_TOKEN: 'token',
    SESSION_EXPIRED_CODES: [30007, 30008, 30012],
    defineStore: ({ state, getters, actions }) => {
      const store = Object.assign(state(), actions);
      for (const [name, getter] of Object.entries(getters)) {
        Object.defineProperty(store, name, { get: () => getter(store) });
      }
      return () => store;
    },
    mallAuthApi: { getProfile: () => getProfile(++calls) },
    setSessionExpiredHandler() {},
    xsySentry: { captureError() {} },
    useMallThemeStore: () => ({ refresh() {} }),
    checkAppLogin() {},
    useNetworkStatus: () => ({ isConnected: {} }),
    watch: (_, callback) => {
      onNetworkChange = callback;
    },
    uni: {
      getStorageSync: () => token,
      setStorageSync: (_, value) => {
        token = value;
      },
      removeStorageSync: () => {
        token = '';
      },
      showToast() {},
      reLaunch: ({ url }) => routes.push(url),
    },
  });
  const storeSource = withoutImports(read('../src/store/modules/system/user.js')).replace('export const useUserStore', 'const useUserStore');
  const appSource = withoutImports(read('../src/App.vue').match(/<script>([\s\S]*?)<\/script>/)[1]).replace('export default', 'globalThis.app =');
  vm.runInContext(`${storeSource}\n${appSource}\nglobalThis.user = useUserStore();`, context);
  return {
    user: context.user,
    app: context.app,
    reconnect: () => onNetworkChange(true),
    token: () => token,
    calls: () => calls,
    routes,
  };
}

test('failed cold start keeps the cached token without claiming login, then reconnect restores the profile once', async () => {
  const h = harness(async (attempt) => {
    if (attempt === 1) throw new Error('network unavailable');
    return { data: profile };
  });
  h.app.onLaunch();
  assert.equal(h.calls(), 1);
  await h.user.getLoginInfo();
  assert.equal(h.user.isLogin, false);
  assert.equal(h.token(), 'cached-token');

  h.reconnect();
  h.app.onShow();
  assert.equal(h.calls(), 2);
  await h.user.getLoginInfo();
  assert.equal(h.user.isLogin, true);
  assert.equal(h.user.customerId, profile.customerId);
  h.app.onShow();
  assert.equal(h.calls(), 2);
});

test('returning to the foreground retries an incomplete profile and still enforces password change', async () => {
  const h = harness(async (attempt) => ({ data: attempt === 1 ? null : { ...profile, firstPasswordChange: true } }));
  await h.user.getLoginInfo();
  assert.equal(h.user.isLogin, false);
  assert.equal(h.token(), 'cached-token');
  h.app.onShow();
  assert.equal(h.calls(), 2);
  await h.user.getLoginInfo();
  assert.deepEqual(h.routes, ['/pages/account/password']);
});

test('a late cached profile cannot replace a newer explicit login or redirect it', async () => {
  let resolve;
  const h = harness(
    () =>
      new Promise((done) => {
        resolve = done;
      })
  );
  const pending = h.user.getLoginInfo();
  assert.equal(h.user.isLogin, false);
  h.user.setUserLoginInfo({ token: 'new-token', profile: { ...profile, customerId: 9 } });
  resolve({ data: { ...profile, firstPasswordChange: true } });
  await pending;
  assert.equal(h.user.customerId, 9);
  assert.equal(h.token(), 'new-token');
  assert.deepEqual(h.routes, []);
});

test('an expired cached session is removed instead of being retried on every foreground event', async () => {
  const h = harness(async () => {
    throw { code: 30007 };
  });
  await h.user.getLoginInfo();
  assert.equal(h.user.isLogin, false);
  assert.equal(h.token(), '');
  h.app.onShow();
  assert.equal(h.calls(), 1);
});
