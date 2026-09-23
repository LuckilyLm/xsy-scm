/**
 * Wave 3 §5.2 录单草稿「离页不丢」前端契约单测。
 *
 * 草稿只在主动点「关闭」时落本地，曾是这个能力最容易被误改回去的地方：刷新、切换路由、
 * 直接离开页面都不经过 `closeDrawer()`，一旦有人把它退回成单一关闭路径，页面上看不出任何异常，
 * 用户却会整单丢失。这里锁三条：
 * 1. 三类离页通道（beforeunload / 路由离开 / 组件卸载）都挂到同一个 `persistUnsavedDraft`；
 * 2. 写草稿只有 `persistUnsavedDraft` 一处出口，且抽屉已关闭时不写（创建成功后不得把已保存的单写回草稿）；
 * 3. `beforeunload` 监听必须被摘除，不给 keep-alive 页面留重复监听。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

/** 剥掉模板 / 块 / 行注释，门禁只针对代码。 */
const src = readFileSync(
  new URL('../src/views/business/scm/order/components/order-form-drawer.vue', import.meta.url), 'utf8')
  .replace(/<!--[\s\S]*?-->/g, '')
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/^\s*\/\/.*$/gm, '');

test('all three off-page channels persist the draft through the same entry point', () => {
  assert.match(src, /window\.addEventListener\('beforeunload',\s*persistUnsavedDraft\)/);
  assert.match(src, /onBeforeRouteLeave\(\(\)\s*=>\s*persistUnsavedDraft\(\)\)/);
  assert.match(src, /onBeforeUnmount\(\(\)\s*=>\s*\{[\s\S]*?persistUnsavedDraft\(\);/);
});

test('draft writing has a single exit that refuses while the drawer is closed', () => {
  const writeCalls = src.match(/writeDraft\(serializeDraft/g) ?? [];
  assert.equal(writeCalls.length, 1, '落草稿只应有 persistUnsavedDraft 一个出口');
  assert.match(src, /function persistUnsavedDraft\(\)\s*\{\s*if\s*\(!visible\.value\)\s*return;/);
  assert.match(src, /function closeDrawer\(\)\s*\{\s*persistUnsavedDraft\(\);/);
});

test('the beforeunload listener is removed on both close and unmount', () => {
  const removeCalls = src.match(/window\.removeEventListener\('beforeunload',\s*persistUnsavedDraft\)/g) ?? [];
  assert.equal(removeCalls.length, 2, '关闭抽屉与组件卸载都要摘掉 beforeunload 监听');
});
