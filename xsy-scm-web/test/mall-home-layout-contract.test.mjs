import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const read = path => readFileSync(new URL(path, import.meta.url), 'utf8');
const api = read('../src/api/business/scm/mall-home-api.ts');
const page = read('../src/views/business/scm/mall/mall-home-layout.vue');

test('首页草稿、发布和回滚提交各自的乐观锁版本与原因', () => {
  assert.match(api, /admin\/draft', \{version, blocks\}/);
  assert.match(api, /admin\/publish', \{draftVersion, pointerVersion\}/);
  assert.match(api, /admin\/rollback', \{targetReleaseId, pointerVersion, reason\}/);
  assert.match(page, /:disabled="dirty \|\| !blocks\.length"[^>]*@click="publish"/);
  assert.match(page, /mallHomeApi\.publish\(draftVersion\.value, pointerVersion\.value\)/);
  assert.match(page, /mallHomeApi\.rollback\(rollbackTarget\.value\.id, pointerVersion\.value, rollbackReason\.value\.trim\(\)\)/);
  assert.match(page, /mallHomeApi\.preview\(previewCustomerId\.value, previewWarehouseId\.value\)/);
});

test('装修页操作和抽屉遵守 SCM 页面约定', () => {
  const action = page.match(/\{title: '操作', dataIndex: 'action', align: 'center', width: (\d+)\}/);
  assert.ok(action);
  assert.ok(Number(action[1]) <= 180);
  assert.match(page, /:width="scmDrawerWidth\('s'\)"/);
  assert.match(page, /<ScmActionMore v-if="hasPermission\('scm:mall:home:edit'\)"/);
  assert.doesNotMatch(page, /var\(--scm-border-color\)|var\(--scm-fill-color\)/);
});
