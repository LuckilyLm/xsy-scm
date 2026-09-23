/**
 * Wave 8 §12.2 列表查询条件「按用户 + 页面」本地记忆前端契约单测。
 *
 * 钉死这一波最容易被日后改动悄悄破坏的边界：
 * 1. 存储键纯函数：不同 employeeId / 不同 pageKey 生成不同键，且带统一前缀（用户之间不串味）；
 * 2. 客户列表页确实接入组合式：查询时保存、重置时清空、挂载时恢复并强制回到第 1 页；
 * 3. 深链详情类页面（customer-detail）绝不调用查询记忆组合式，避免旧筛选污染深链上下文。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {queryFilterStorageKey, QUERY_FILTER_KEY_PREFIX} from '../src/lib/query-filter-key.ts';

/** 读源码并剥掉注释 —— 门禁只针对代码，头注释里提到被禁用的名字是合规的。 */
function code(relative) {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '');
}

const LIST = '../src/views/business/scm/customer/customer-list.vue';
const DETAIL = '../src/views/business/scm/customer/customer-detail.vue';

test('存储键按用户与页面隔离，带统一前缀', () => {
  const keyA = queryFilterStorageKey('44', 'scm:customer:list');
  const keyB = queryFilterStorageKey('45', 'scm:customer:list');
  const keyC = queryFilterStorageKey('44', 'scm:order:list');
  assert.ok(keyA.startsWith(QUERY_FILTER_KEY_PREFIX + ':'), '键须带统一前缀');
  // 不同用户 → 不同键（不串味）
  assert.notEqual(keyA, keyB);
  // 同用户不同页面 → 不同键
  assert.notEqual(keyA, keyC);
  // 结构可复核：前缀:用户:页面
  assert.equal(keyA, 'xsy-scm:query-filter:44:scm:customer:list');
});

test('客户列表页接入查询记忆：查询保存、重置清空、挂载恢复并回到第 1 页', () => {
  const vue = code(LIST);
  assert.match(vue, /import \{useQueryFilterMemory\} from '\/@\/lib\/query-filter-memory';/);
  assert.match(vue, /const queryMemory = useQueryFilterMemory<CustomerQuery>\('scm:customer:list'\);/);
  // 查询：先回到第 1 页再落偏好，落的是当前筛选快照
  assert.match(vue, /function search\(\)\s*\{\s*filters\.pageNum = 1;\s*queryMemory\.save\(\{\.\.\.filters\}\);/);
  // 重置：清空偏好
  assert.match(vue, /function reset\(\)\s*\{[\s\S]*?queryMemory\.clear\(\);/);
  // 挂载：恢复记忆后强制 pageNum=1，不把用户带回旧页码
  assert.match(vue, /Object\.assign\(filters, queryMemory\.load\(\), \{pageNum: 1\}\);/);
  // 模板可达性：光有 search() 实现没用，按钮必须真的绑到它（历史上这里是死按钮）
  assert.match(vue, /<a-button type="primary" @click="search">查询<\/a-button>/);
});

test('深链详情页不接入查询记忆，避免旧筛选污染 customerId 上下文', () => {
  const vue = code(DETAIL);
  assert.doesNotMatch(vue, /useQueryFilterMemory/);
  assert.doesNotMatch(vue, /query-filter/);
});
