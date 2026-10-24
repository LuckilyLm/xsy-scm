/**
 * 金额与数量精度契约测试。
 *
 * 口径：销售金额/售价两位，采购价/成本/数量/费率四位；提交前统一归一到四位定点字符串。
 * 这里只测纯函数，页面展示由各模块契约测试覆盖。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {
  formatAmount,
  formatAmountOrDash,
  formatPrice,
  formatQuantity,
  isDecimalScale,
  moneyPayload,
  normalizeMoneyInput,
  toScale,
} from '../src/utils/scm-amount.ts';

test('金额按两位展示，0 与缺失不合并', () => {
  assert.equal(formatAmount('0.0000'), '¥ 0.00');
  assert.equal(formatAmount('12.3'), '¥ 12.30');
  assert.equal(formatAmount('12.3456'), '¥ 12.35');
  assert.equal(formatAmount('1234567.5'), '¥ 1,234,567.50');
  assert.equal(formatAmount(null), '未定价');
  assert.equal(formatAmount(undefined), '未定价');
  assert.equal(formatAmountOrDash(null), '—');
  assert.equal(formatAmountOrDash('0.0000'), '¥ 0.00');
});

test('销售价两位，采购价与成本四位，数量四位', () => {
  assert.equal(formatPrice('12.3456', 'sales'), '¥ 12.35');
  assert.equal(formatPrice('12.3456', 'purchase'), '¥ 12.3456');
  assert.equal(formatPrice('12.3456', 'cost'), '¥ 12.3456');
  assert.equal(formatQuantity('0.0000'), '0.0000');
  assert.equal(formatQuantity('2'), '2.0000');
  assert.equal(formatQuantity(null), '—');
});

test('录入归一两位、提交归一四位', () => {
  assert.equal(normalizeMoneyInput('12.3456'), '12.35');
  assert.equal(normalizeMoneyInput('12'), '12.00');
  assert.equal(normalizeMoneyInput(null), '');
  assert.equal(moneyPayload('12.3456'), '12.3500');
  assert.equal(moneyPayload('12.3'), '12.3000');
  assert.equal(moneyPayload(null), '');
  assert.equal(toScale('1.00005', 4), '1.0001');
});

test('小数位校验拒绝三位和五位，放行历史四位', () => {
  assert.equal(isDecimalScale('12.34', 2), true);
  assert.equal(isDecimalScale('12.345', 2), false);
  assert.equal(isDecimalScale('12.3456', 2), false);
  assert.equal(isDecimalScale('12.3456', 4), true);
  assert.equal(isDecimalScale('12.34567', 4), false);
  assert.equal(isDecimalScale('-1', 2), false);
  assert.equal(isDecimalScale('0', 2, {allowZero: false}), false);
});
