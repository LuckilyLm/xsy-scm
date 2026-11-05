import test from 'node:test';
import assert from 'node:assert/strict';
import {upgradeStoredSettings} from '../src/utils/xsy-storage-upgrade.ts';

function storage(entries) {
  const values = new Map(entries);
  return {
    get length() {
      return values.size;
    },
    key(index) {
      return [...values.keys()][index] ?? null;
    },
    getItem(key) {
      return values.get(key) ?? null;
    },
    setItem(key, value) {
      values.set(key, value);
    },
    removeItem(key) {
      values.delete(key);
    },
  };
}

test('升级时保留原登录和偏好，不改变其他应用的配置', () => {
  const saved = storage([
    ['smart_admin_user_token', 'token'],
    ['smart_admin_app_config', '{"theme":"dark"}'],
    ['other', 'value'],
  ]);
  upgradeStoredSettings(saved);
  assert.equal(saved.getItem('xsy_user_token'), 'token');
  assert.equal(saved.getItem('xsy_app_config'), '{"theme":"dark"}');
  assert.equal(saved.getItem('smart_admin_user_token'), null);
  assert.equal(saved.getItem('other'), 'value');
});

test('已有的新配置优先，重复升级不覆盖', () => {
  const saved = storage([
    ['smart_admin_app_config', 'old'],
    ['xsy_app_config', 'current'],
  ]);
  upgradeStoredSettings(saved);
  upgradeStoredSettings(saved);
  assert.equal(saved.getItem('xsy_app_config'), 'current');
  assert.equal(saved.getItem('smart_admin_app_config'), null);
});

test('浏览器拒绝写入时保留原值', () => {
  const saved = storage([['smart_admin_user_token', 'token']]);
  saved.setItem = () => {
    throw new Error('quota');
  };
  upgradeStoredSettings(saved);
  assert.equal(saved.getItem('smart_admin_user_token'), 'token');
  assert.equal(saved.getItem('xsy_user_token'), null);
});
