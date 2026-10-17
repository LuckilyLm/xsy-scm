import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const read = path => readFileSync(new URL(path, import.meta.url), 'utf8');
const api = read('../src/api/business/scm/purchase-inquiry-api.ts');
const page = read('../src/views/business/scm/purchase/purchase-inquiry-list.vue');
const batchDrawer = read('../src/views/business/scm/purchase/components/purchase-demand-batch-detail-drawer.vue');

test('询价与报价写请求复用采购幂等封装，确认需明确版本', () => {
  assert.match(api, /purchaseCommand<PurchaseInquiryDetail>\(prefix \+ '\/create', data\)/);
  assert.match(api, /purchaseCommand<PurchaseInquiryDetail>\(prefix \+ '\/quote', data\)/);
  assert.match(api, /purchaseCommand<PurchaseInquiryDetail>\(prefix \+ '\/accept', data\)/);
  assert.match(page, /quoteId: quote\.id, version: current\.version/);
  assert.match(page, /采购单仍需单独创建和提交/);
});

test('询价批次分页和报价修订历史都使用服务端分页', () => {
  assert.match(api, /postRequest\(prefix \+ '\/batch', \{batchId, page\}\)/);
  assert.match(api, /postRequest\(prefix \+ '\/history', \{inquiryId, beforeId\}\)/);
  assert.match(page, /quoteHistoryMore/);
  assert.match(page, /onPageChange/);
  assert.match(page, /c\.existingInquiryId/);
});

test('询价从冻结批次进入，当前报价与历史版本分别展示', () => {
  assert.match(batchDrawer, /\/purchase\/purchase-inquiry-list/);
  assert.match(page, /detail\.currentOffers/);
  assert.match(page, /detail\.quotes/);
  assert.match(page, /scmDrawerWidth\('workspace'\)/);
  assert.match(page, /v-privilege="'scm:purchase:inquiry:accept'"/);
});
