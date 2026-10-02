import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');

test('ADM-02 return receipt command is exposed with idempotent API and dispositions', () => {
  const api = read('src/api/business/scm/order-return-api.ts');
  const page = read('src/views/business/scm/order/order-return-list.vue');
  assert.match(api, /\/scm\/order\/return\/receive/);
  assert.match(page, /RETURN_TO_STOCK/);
  assert.match(page, /DAMAGE/);
  assert.match(page, /warehouseId/);
});

test('ADM-03 settlement customer is explicit in customer forms and details', () => {
  const types = read('src/types/business/scm/customer.d.ts');
  const form = read('src/views/business/scm/customer/components/customer-form-drawer.vue');
  const detail = read('src/views/business/scm/customer/customer-detail.vue');
  assert.match(types, /settlementCustomerId/);
  assert.match(form, /统一结算方/);
  assert.match(detail, /settlementCustomerName/);
});

test('ADM-04 credit check contract is exposed', () => {
  const api = read('src/api/business/scm/order-api.ts');
  assert.match(api, /\/scm\/order\/credit-check\//);
  assert.match(api, /projectedExposure/);
  assert.match(api, /overdue/);
});
