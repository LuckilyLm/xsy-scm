import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');
const { displayMoney } = await import(`data:text/javascript;base64,${Buffer.from(read('../src/utils/money-display.js')).toString('base64')}`);

test('sales and mall amounts display two decimals with HALF_UP rounding', () => {
  assert.equal(displayMoney('0.0000'), '0.00');
  assert.equal(displayMoney('12.3456'), '12.35');
  assert.equal(displayMoney('12.3449'), '12.34');
  assert.equal(displayMoney('12.3450'), '12.35');
  assert.equal(displayMoney('0.0050'), '0.01');
  assert.equal(displayMoney('199.9900'), '199.99');
  assert.equal(displayMoney('-1.0050'), '-1.01');
  assert.equal(displayMoney('8'), '8.00');
});

test('empty or already formatted values never become fake zero', () => {
  assert.equal(displayMoney(null), '—');
  assert.equal(displayMoney(undefined), '—');
  assert.equal(displayMoney(''), '—');
  assert.equal(displayMoney('询价'), '询价');
});

test('money and quantity use separate display rules', () => {
  const card = read('../src/components/business/product-card.vue');
  const detail = read('../src/pages-sub/product/detail.vue');
  const cart = read('../src/pages/cart/index.vue');
  const orderDisplay = read('../src/utils/order-display.js');
  // 售价两位；数量仍按四位定点
  assert.match(card, /displayMoney\(product\.price\)/);
  assert.match(detail, /displayMoney\(product\.price\)/);
  assert.match(cart, /displayMoney\(item\.unitPrice\)/);
  assert.match(orderDisplay, /export function displayQuantity[\s\S]*?10000/);
  // 金额统一走 displayMoney，不再保留直接输出四位原值的旧函数
  assert.doesNotMatch(orderDisplay, /export function displayAmount/);
  const wallet = read('../src/pages-sub/wallet/index.vue');
  const credit = read('../src/pages-sub/credit/index.vue');
  assert.match(wallet, /displayMoney\(summary\.availableBalance\)/);
  assert.match(credit, /displayMoney\(summary\.openReceivableAmount\)/);
  // 0 报价按无价处理，页面显示「询价」而不是 ¥0.00
  assert.match(read('../src/utils/product-display.js'), /value > 0/);
});
