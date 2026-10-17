import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
import {transformSync} from 'esbuild';

const require = createRequire(import.meta.url);
const source = readFileSync(new URL('../src/views/business/scm/order/order-batch-price-check.ts', import.meta.url), 'utf8');
const compiled = transformSync(source, {loader: 'ts', format: 'cjs', target: 'es2022'}).code;
const module = {exports: {}};
new Function('require', 'module', 'exports', compiled)(require, module, module.exports);
const {compareOrderPrices, canApplyPriceCheck} = module.exports;

const order = items => ({
  orderId: '9007199254740993', orderNo: 'SO-123', version: 3, status: 'DRAFT',
  customerId: '9', customerNameSnapshot: '甲客户', items
});
const item = (skuId, price, manual = false) => ({
  itemId: String(skuId), skuId: String(skuId), orderedQuantity: '2.0000',
  draftUnitPrice: price, manualPriceOverride: manual, productNameSnapshot: '商品' + skuId,
  skuCodeSnapshot: 'A' + skuId
});
const priced = (skuId, unitPrice, sellable = true) => ({
  skuId: String(skuId), unitPrice, sellable, priceStatus: unitPrice == null ? 'UNPRICED' : 'PRICED',
  priceSource: unitPrice == null ? null : 'MARKET'
});

test('订单核价使用定点十进制比较，不写回历史草稿价或订单版本', () => {
  const input = order([item(1, '1.1000'), item(2, '3.0000'), item(3, '0.0000')]);
  const before = JSON.stringify(input);
  const result = compareOrderPrices(input, [
    priced(1, '1.1000'), priced(2, '2.9000'), priced(3, '0.0000')
  ]);
  assert.deepEqual(result.lines.map(row => row.state), ['SAME', 'DOWN', 'SAME']);
  assert.equal(result.changed, 1);
  assert.equal(result.version, 3);
  assert.equal(JSON.stringify(input), before);
  assert.equal(canApplyPriceCheck(result), true);
});

test('人工改价不参与重算；缺失价格与不可售均阻止人工应用', () => {
  const input = order([item(1, '12.0000', true), item(2, '1.0000'), item(3, '2.0000')]);
  const result = compareOrderPrices(input, [
    priced(1, '15.0000'), priced(2, null), priced(3, '5.0000', false)
  ]);
  assert.deepEqual(result.lines.map(row => row.state), ['MANUAL', 'BLOCKED', 'BLOCKED']);
  assert.equal(result.lines[0].currentPrice, '12.0000');
  assert.equal(result.blocked, 2);
  assert.equal(canApplyPriceCheck(result), false);
});

test('原草稿未定价但已有现价时标记为新定价，涨价与降价分别展示', () => {
  const result = compareOrderPrices(order([
    item(1, null), item(2, '5.0000'), item(3, '5.0000')
  ]), [priced(1, '0.0000'), priced(2, '6.0000'), priced(3, '4.0000')]);
  assert.deepEqual(result.lines.map(row => row.state), ['NEW', 'UP', 'DOWN']);
  assert.equal(result.changed, 3);
});

test('拒绝对已确认订单生成草稿核价报告', () => {
  assert.throws(() => compareOrderPrices({...order([]), status: 'CONFIRMED'}, []), /草稿订单/);
  assert.throws(() => compareOrderPrices({...order([]), version: undefined}, []), /草稿订单/);
});

test('UI 禁止直接批量改价：每次操作基于可见订单详情和版本核对', () => {
  const modal = readFileSync(new URL('../src/views/business/scm/order/components/order-batch-price-check-modal.vue', import.meta.url), 'utf8');
  const list = readFileSync(new URL('../src/views/business/scm/order/order-list.vue', import.meta.url), 'utf8');
  assert.match(list, /hasPermission\('scm:order:query'\)/);
  assert.match(list, /hasPermission\('scm:pricing:resolve:query'\)/);
  assert.match(list, /selected\.length > 8/);
  assert.match(modal, /orderApi\.detail\(entry\.id/);
  assert.match(modal, /orderApi\.preview\(/);
  assert.match(modal, /latest\.version !== previewVersion/);
  assert.match(modal, /orderApi\.reprice\(\{orderId: entry\.id, version: previewVersion, reason: actionReason\}\)/);
  assert.doesNotMatch(modal, /orderApi\.createAndProgress\(|orderApi\.confirm\(|orderApi\.update\(/);
});
