import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const read = p => readFileSync(new URL(p, import.meta.url), 'utf8');
const type = read('../src/views/business/scm/print/print-types.ts');
const list = read('../src/views/business/scm/order/order-list.vue');
const detail = read('../src/views/business/scm/order/order-detail-page.vue');
const modal = read('../src/views/business/scm/print/print-document-modal.vue');
const provider = read('../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/print/support/SalesOrderPrintSourceProvider.java');
const service = read('../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/print/service/ScmPrintService.java');
const migration = read('../../xsy-scm-server/sa-admin/src/main/resources/db/migration/V146__scm_sales_order_print.sql');

test('复用打印中心的模板、快照与记录，不另建订单打印写接口', () => {
  assert.match(type, /'SALES_ORDER'/);
  assert.match(detail, /document-type="SALES_ORDER"/);
  assert.match(list, /document-type="SALES_ORDER"/);
  assert.match(modal, /printApi\.preview\(/);
  assert.match(modal, /printApi\.print\(/);
  assert.match(service, /scmPrintRecordDao\.insertRecord\(record\)/);
  assert.match(service, /scmIdempotencyService\.claim\(/);
});

test('订单打印来源从正式订单查询获取，金额与行项目使用原订单快照', () => {
  assert.match(provider, /return OrderPermission\.QUERY/);
  assert.match(provider, /orderQueryService\.detail\(businessId\)/);
  assert.match(provider, /getLockedUnitPrice\(\)/);
  assert.match(provider, /getDraftUnitPrice\(\)/);
  assert.match(provider, /getSettlementTotalAmount\(\)/);
  assert.doesNotMatch(provider, /PriceResolver|update\(|insert\(|selectById\(/);
});

test('默认模板与 DB 类型约束同步扩展；批量打印有数量上限', () => {
  assert.match(migration, /ck_scm_print_record_document_type/);
  assert.match(migration, /SALES_ORDER_DEFAULT/);
  assert.match(migration, /"columns"/);
  assert.match(list, /selected\.length > 10/);
  assert.match(list, /selectedRows\.value\.length !== selected\.value\.length/);
  assert.match(detail, /hasPermission\('scm:order:query'\)/);
});
