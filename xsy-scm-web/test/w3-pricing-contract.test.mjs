import test from 'node:test';
import assert from 'node:assert/strict';
import { formatAmount, formatAmountOrDash } from '../src/utils/scm-amount.ts';

test('W3 amount contract keeps zero distinct from missing price', () => {
  assert.equal(formatAmount('0.0000'), '¥ 0.0000');
  assert.equal(formatAmount(null), '未定价');
  assert.equal(formatAmount(undefined), '未定价');
  assert.equal(formatAmount('1234567.5'), '¥ 1,234,567.5000');
  assert.equal(formatAmountOrDash(null), '—');
});

test('W3 status contract has one unpriced reason and five unavailable reasons', async () => {
  const constants = await import('../src/constants/business/scm/pricing-const.ts');
  assert.deepEqual(Object.keys(constants.UNPRICED_REASON_ENUM), ['NO_PRICE_SOURCE']);
  assert.deepEqual(Object.keys(constants.UNAVAILABLE_REASON_ENUM).sort(), [
    'CATEGORY_DISABLED', 'NOT_VISIBLE', 'SKU_NOT_FOUND', 'SKU_OFF_SHELF', 'SPU_OFF_SHELF',
  ]);
});

test('W3 pricing error mapping preserves overlap, conflict, idempotency and sellability codes', async () => {
  const { pricingError } = await import('../src/views/business/scm/pricing/pricing-errors.ts');
  assert.match(pricingError({ code: 40933 }), /重叠/);
  assert.match(pricingError({ code: 40935 }), /重叠/);
  assert.match(pricingError({ code: 40921 }), /修改/);
  assert.match(pricingError({ code: 40948 }), /批次号/);
  assert.match(pricingError({ code: 40949 }), /不可售/);
});
