import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import test from 'node:test';
import {URL} from 'node:url';

/*
 * 价格来源是后端枚举直出的字段：商城只要漏一个映射，商品卡就会把英文常量
 * 端给客户。这条契约从后端 ScmPriceSourceEnum 反查商城两处映射，
 * 新增来源时漏改会当场失败（与主前端 scm-price-source-contract 同一思路）。
 */

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');

const backendEnum = read('../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/pricing/constant/ScmPriceSourceEnum.java');
const adapter = read('../src/api/mall/product-adapter.js');
const display = read('../src/utils/product-display.js');
const card = read('../src/components/business/product-card.vue');
const detail = read('../src/pages-sub/product/detail.vue');

function backendSources() {
  // 枚举常量形如 `AGREEMENT("客户协议价"),`：取左括号前的大写标识符
  return [...backendEnum.matchAll(/^\s{4}([A-Z_]+)\(/gm)].map((m) => m[1]);
}

test('后端价格来源枚举解析非空，避免契约空转', () => {
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
  const adapterKeys = [...adapter.matchAll(/^  ([A-Z_]+): '/gm)].map((m) => m[1]);
  for (const key of adapterKeys) {
    assert.ok(sources.includes(key), `product-adapter 的 ${key} 在后端枚举中不存在`);
  }
});

test('时价单独成档：档位函数与两处配色都认识 time 档', () => {
  assert.match(display, /source === 'TIME_PRICE'\) \{\s*\n\s*return 'time';/);
  assert.match(card, /&--time \{/);
  assert.match(detail, /&--time \{/);
});

test('商品卡主色读主题变量：首页切主题时卡内加购与价格同步变色', () => {
  // 主题变量只挂在首页根节点；其他页面未定义时回落默认绿，观感不变
  assert.doesNotMatch(card, /\$color-primary/);
  const varCount = (card.match(/var\(--mall-primary/g) || []).length;
  assert.ok(varCount >= 10, `商品卡应全部改读主题变量，当前 ${varCount} 处`);
});
