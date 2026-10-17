import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const read = file => readFileSync(new URL(file, import.meta.url), 'utf8');

const product = read('../src/views/business/scm/product/product-list.vue');
const sales = read('../src/views/business/scm/report/report-sales-list.vue');
const purchase = read('../src/views/business/scm/report/report-purchase-list.vue');
const productApi = read('../src/api/business/scm/product-api.ts');
const reportApi = read('../src/api/business/scm/report-api.ts');
const reportController = read('../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/report/controller/ScmReportController.java');
const productService = read('../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/product/service/ProductQueryService.java');

test('动态条件来自服务端权限约束的字段列表', () => {
  assert.match(productApi, /query\/filters/);
  assert.match(reportApi, /sales\/order\/query\/filters/);
  assert.match(reportApi, /purchase\/product\/query\/filters/);
  assert.match(reportController, /@SaCheckPermission\(ScmReportPermission\.SALES_QUERY\)/);
  assert.match(productService, /ProductAdvancedFilters\.apply\(form\)/);
});

test('三个试点页面共用条件编辑器且筛选作用于原查询和导出', () => {
  for (const page of [product, sales, purchase]) {
    assert.match(page, /ScmAdvancedFilterBuilder/);
    assert.match(page, /v-model="advancedFilters"/);
    assert.match(page, /advancedFilters:\s*(?:activeTab\.value\s*===\s*'(?:order|product)'\s*&&\s*)?advancedFilters\.value\.length/);
  }
  assert.match(sales, /tab === order && advancedFilters\.value\.length/);
  assert.match(purchase, /tab === product && advancedFilters\.value\.length/);
});
