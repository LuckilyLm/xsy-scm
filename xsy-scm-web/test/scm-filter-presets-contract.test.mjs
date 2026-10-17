import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const read = file => readFileSync(new URL(file, import.meta.url), 'utf8');
const api = read('../src/api/business/scm/filter-preset-api.ts');
const component = read('../src/components/business/scm/advanced-filter/saved-presets.vue');
const service = read('../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/preference/filter/UserFilterPresetService.java');
const dao = read('../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/preference/filter/UserFilterPresetDao.java');
const product = read('../src/views/business/scm/product/product-list.vue');
const sales = read('../src/views/business/scm/report/report-sales-list.vue');
const purchase = read('../src/views/business/scm/report/report-purchase-list.vue');

test('保存方案从登录上下文取得所有者，不允许客户端指定其它用户', () => {
  assert.match(service, /ScmOperator\.current\(\)/);
  assert.match(service, /StpUtil\.checkPermission/);
  assert.match(service, /ScmAdvancedFilterCatalog\.resolve/);
  assert.match(dao, /owner_key = #\{ownerKey\} AND view_key = #\{viewKey\}/);
  assert.doesNotMatch(api, /ownerKey|userId/);
});

test('三个页面均复用方案保存应用删除，切页隔离方案类别', () => {
  assert.match(component, /scmFilterPresetApi\.create/);
  assert.match(component, /scmFilterPresetApi\.remove/);
  assert.match(component, /preset\.conditions\.map\(c => \(\{\.\.\.c\}\)\)/);
  assert.match(product, /view-key="PRODUCT"/);
  assert.match(sales, /view-key="SALES_ORDER"/);
  assert.match(purchase, /view-key="PURCHASE_PRODUCT"/);
  for (const page of [product, sales, purchase]) {
    assert.match(page, /@apply="applySavedFilters"/);
    assert.match(page, /advancedFilters\.value = conditions/);
  }
});
