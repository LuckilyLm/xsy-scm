import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {test} from 'node:test';

/*
 * 价格来源是后端枚举直出的字段：前端只要漏一个映射，订单行就会把英文常量端给使用者。
 * 这条契约把「后端枚举 = 前端映射键」钉死，新增来源时漏改会当场失败。
 */

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');

const backend = read('../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/pricing/constant/ScmPriceSourceEnum.java');
const orderConst = read('../src/constants/business/scm/order-const.ts');
const pricingConst = read('../src/constants/business/scm/pricing-const.ts');
const display = read('../src/views/business/scm/pricing/pricing-display.ts');
const preview = read('../src/views/business/scm/pricing/price-preview.vue');

/** 从 Java 枚举体里取常量名：形如 `AGREEMENT("客户协议价"),`，取左括号前的标识符。 */
function backendSources() {
  return [...backend.matchAll(/^\s{4}([A-Z_]+)\(/gm)].map((m) => m[1]);
}

test('后端价格来源枚举非空，避免契约空转', () => {
  const sources = backendSources();
  assert.ok(sources.length >= 4, `解析到的来源过少：${sources.join(',')}`);
  assert.ok(sources.includes('TIME_PRICE'));
});

test('订单行与定价常量都覆盖后端每一个价格来源', () => {
  for (const source of backendSources()) {
    assert.match(orderConst, new RegExp(`${source}: \\{value: '${source}'`), `订单常量缺 ${source}`);
    assert.match(pricingConst, new RegExp(`${source}: \\{value: '${source}'`), `定价常量缺 ${source}`);
  }
});

test('价格来源的中文文案与颜色档位覆盖每一个来源', () => {
  for (const source of backendSources()) {
    // MARKET 是兜底来源（读 SKU 自身市场价），不产生价格历史记录，历史文案不要求它
    if (source !== 'MARKET') {
      assert.match(display, new RegExp(`${source}: '`), `历史来源文案缺 ${source}`);
    }
    assert.match(preview, new RegExp(`${source}: '`), `预览页颜色档位缺 ${source}`);
  }
});
