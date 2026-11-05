import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import test from 'node:test';
import {URL} from 'node:url';

/*
 * 价格来源是后端枚举直出的字段：商城只要漏一个映射，商品详情就会把英文常量
 * 端给客户。列表不展示来源标签，这条约定仍反查商城两处映射，
 * 新增来源时漏改会当场失败（与主前端 scm-price-source-contract 同一思路）。
 */

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');

const backendEnum = read('../../xsy-scm-server/xsy-admin/src/main/java/com/xsy/scm/pricing/constant/ScmPriceSourceEnum.java');
const adapter = read('../src/api/mall/product-adapter.js');
const display = read('../src/utils/product-display.js');
const card = read('../src/components/business/product-card.vue');
const detail = read('../src/pages-sub/product/detail.vue');

function backendSources() {
  // 枚举常量形如 `AGREEMENT("客户协议价"),`：取左括号前的大写标识符
  return [...backendEnum.matchAll(/^\s{4}([A-Z_]+)\(/gm)].map((m) => m[1]);
}

test('后端价格来源枚举解析非空，避免约定空转', () => {
  const sources = backendSources();
  assert.ok(sources.length >= 4);
  assert.ok(sources.includes('TIME_PRICE'));
});

test('商城两处来源文案覆盖后端每一个价格来源', () => {
  for (const source of backendSources()) {
    assert.match(adapter, new RegExp(`${source}: '`), `product-adapter 缺 ${source}`);
    assert.match(display, new RegExp(`${source}: '`), `product-display 缺 ${source}`);
  }
});

test('映射里不出现后端不存在的来源键', () => {
  const sources = backendSources();
  const adapterKeys = [...adapter.matchAll(/^ {2}([A-Z_]+): '/gm)].map((m) => m[1]);
  for (const key of adapterKeys) {
    assert.ok(sources.includes(key), `product-adapter 的 ${key} 在后端枚举中不存在`);
  }
});

test('价格来源只在详情展示，时价仍有独立文案与配色', () => {
  assert.match(display, /source === 'TIME_PRICE'\) \{\s*\n\s*return 'time';/);
  assert.doesNotMatch(card, /product-card__source|product-card__tag-row|priceSourceLabel|priceSourceLevel/);
  assert.match(detail, /v-if="priced && sourceLabel" class="detail__source"/);
  assert.match(detail, /\{\{\s*sourceLabel\s*\}\}/);
  assert.match(detail, /&--time \{/);
});

test('商品卡主色读主题变量：首页切主题时加购与描边操作同步变色', () => {
  // 主题变量由各页面根节点统一提供；未加载主题时回落默认绿
  assert.doesNotMatch(card, /\$color-primary/);
  assert.match(card, /&--add[^{}]*\{\s*background:\s*var\(--mall-action-gradient,\s*var\(--mall-primary,/);
  for (const kind of ['preorder', 'inquiry']) {
    assert.match(card, new RegExp(`&--${kind}[^{}]*\\{[^{}]*border:\\s*1px solid var\\(--mall-primary,`));
  }
});
