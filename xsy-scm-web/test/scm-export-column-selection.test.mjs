import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const read = file => readFileSync(new URL(file, import.meta.url), 'utf8');
const api = read('../src/api/business/scm/report-api.ts');
const productApi = read('../src/api/business/scm/product-api.ts');
const sales = read('../src/views/business/scm/report/report-sales-list.vue');
const purchase = read('../src/views/business/scm/report/report-purchase-list.vue');
const product = read('../src/views/business/scm/product/product-list.vue');

const reportController = read('../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/report/controller/ScmReportController.java');
const productController = read('../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/product/controller/ProductExcelController.java');

test('导出只投影原权限查询，采购成本列仍受额外成本授权', () => {
  assert.match(reportController, /salesReportService\.orderList\(form\)/);
  assert.match(reportController, /purchaseReportService\.byProduct\(form\)/);
  assert.match(reportController, /StpUtil\.hasPermission\(ScmReportPermission\.COST_QUERY\)/);
  assert.match(productController, /productQueryService\.query\(form\)/);
});

test('三个试点的自选列均由服务端白名单下发，并与原列表筛选共用请求参数', () => {
  assert.match(api, /sales\/order\/export\/columns/);
  assert.match(api, /purchase\/product\/export\/columns/);
  assert.match(productApi, /product\/export\/columns/);
  assert.match(sales, /exportColumnKeys: orderExportKeys\.value\.length/);
  assert.match(purchase, /exportColumnKeys: purchaseProductExportKeys\.value\.length/);
  assert.match(product, /exportColumnKeys: exportColumnKeys\.value\.length/);
});
