import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {mergeColumn} from '../src/components/support/table-operator/smart-table-column-merge.ts';

function code(relative) {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '');
}

const THRESHOLD = code('../src/views/business/scm/inventory/inventory-warning-threshold-list.vue');
const COLUMN_MERGE = code('../src/components/support/table-operator/smart-table-column-merge.ts');
const VISIBILITY = code('../src/views/business/scm/customer/customer-sku-visibility-list.vue');
const PRODUCT_CONST = code('../src/constants/business/scm/product-const.ts');
const PURCHASE_DEMAND = code('../src/views/business/scm/purchase/purchase-demand-list.vue');
const CUSTOMER_LIST = code('../src/views/business/scm/customer/customer-list.vue');
const INVENTORY_BALANCE = code('../src/views/business/scm/inventory/inventory-balance-list.vue');
const ORDER_LIST = code('../src/views/business/scm/order/order-list.vue');
const ROUTE_LIST = code('../src/views/business/scm/delivery/route-list.vue');
const AGREEMENT_PRICE = code('../src/views/business/scm/pricing/agreement-price-list.vue');
const CUSTOMER_TYPE_PRICE = code('../src/views/business/scm/pricing/customer-type-price-list.vue');
const INVENTORY_CODE_LISTS = [
  code('../src/views/business/scm/inventory/inventory-warning-list.vue'),
  code('../src/views/business/scm/inventory/inventory-reservation-list.vue'),
  code('../src/views/business/scm/inventory/inventory-movement-list.vue'),
  code('../src/views/business/scm/inventory/inventory-outbound-list.vue'),
  code('../src/views/business/scm/inventory/inventory-stocktake-list.vue'),
  code('../src/views/business/scm/inventory/inventory-loss-gain-list.vue'),
  code('../src/views/business/scm/inventory/inventory-conversion-list.vue'),
];

test('库存阈值将仓库、商品、规格和编码放入独立列', () => {
  for (const [title, dataIndex] of [
    ['仓库', 'warehouseName'],
    ['仓库编码', 'warehouseCode'],
    ['商品', 'productName'],
    ['商品规格', 'skuName'],
    ['规格编码', 'skuCode'],
  ]) {
    assert.match(THRESHOLD, new RegExp(`title: '${title}', dataIndex: '${dataIndex}'`));
  }
  assert.doesNotMatch(THRESHOLD, /class="scm-cell-stack/);
  assert.match(THRESHOLD, /dataIndex === 'warehouseCode'[\s\S]{0,120}class="scm-mono"/);
  assert.match(THRESHOLD, /dataIndex === 'skuCode'[\s\S]{0,120}class="scm-mono"/);
});

test('TableOperator respects default hidden columns and keeps new fields visible in older configurations', () => {
  const makeColumns = () => [
    {title: '仓库', dataIndex: 'warehouseName'},
    {title: '仓库编码', dataIndex: 'warehouseCode', showFlag: false},
    {title: '规格编码', dataIndex: 'skuCode'},
  ];
  const defaults = mergeColumn(makeColumns(), []);
  assert.deepEqual(defaults.newColumns.map((column) => column.showFlag), [true, false, true]);

  const olderConfig = mergeColumn(makeColumns(), [
    {columnKey: 'warehouseName', sort: 1, showFlag: true},
  ]);
  assert.deepEqual(olderConfig.newColumns.map((column) => column.showFlag), [true, false, true]);

  const userOptIn = mergeColumn(makeColumns(), [
    {columnKey: 'warehouseCode', sort: 2, showFlag: true},
  ]);
  assert.equal(userOptIn.newColumns.find((column) => column.dataIndex === 'warehouseCode')?.showFlag, true);
  assert.match(COLUMN_MERGE, /fontColumn\.showFlag = fontColumn\.showFlag \?\? true/);
});

test('customer visibility uses Chinese product status labels and keeps unknown values diagnosable', () => {
  assert.match(VISIBILITY, /<ScmStatusTag[\s\S]{0,180}shelfStatusLabel\(record\.spuStatus\)/);
  assert.match(VISIBILITY, /<ScmStatusTag[\s\S]{0,180}shelfStatusLabel\(record\.skuStatus\)/);
  assert.match(VISIBILITY, /value === 'ON_SHELF'[\s\S]{0,100}\? 'success'[\s\S]{0,100}: 'warning'/);
  assert.doesNotMatch(VISIBILITY, /<a-tag>\{\{\s*record\.(spuStatus|skuStatus)/);
  assert.match(PRODUCT_CONST, /return '未知状态'/);
  assert.match(PRODUCT_CONST, /console\.warn\([\s\S]{0,100}\$\{value\}/);
});

test('purchase demand and threshold pages keep rules at the action and validation point', () => {
  assert.match(PURCHASE_DEMAND, /冻结批次生成需求/);
  assert.doesNotMatch(PURCHASE_DEMAND, /按仓库\s*×\s*商品规格\s*×\s*单位冻结批次后生成需求/);
  assert.doesNotMatch(THRESHOLD, /只有配置了阈值的仓库/);
  assert.match(THRESHOLD, /validator: validateAtLeastOneThreshold/);
  assert.match(THRESHOLD, /throw new Error\('预警下限和上限至少填写一个'\)/);
});

test('customer lists expose split code columns through TableOperator', () => {
  for (const [page, titles] of [
    [CUSTOMER_LIST, ['客户编码']],
    [VISIBILITY, ['客户编码', '规格编码']],
  ]) {
    for (const title of titles) {
      assert.match(page, new RegExp(`title: '${title}', dataIndex:`));
    }
  }
  assert.match(CUSTOMER_LIST, /title: '客户编码',[\s\S]{0,100}showFlag: false/);
  assert.match(VISIBILITY, /title: '客户编码',[\s\S]{0,100}showFlag: false/);
  assert.match(VISIBILITY, /title: '规格编码',[\s\S]{0,100}showFlag: false/);
  assert.match(VISIBILITY, /<TableOperator[\s\S]{0,220}SCM_CUSTOMER_SKU_VISIBILITY/);
  assert.doesNotMatch(CUSTOMER_LIST, /class="scm-cell-stack/);
  assert.doesNotMatch(VISIBILITY, /class="scm-cell-stack/);
});

test('inventory, order and pricing lists keep identity codes in separate optional columns', () => {
  for (const [page, fields] of [
    [INVENTORY_BALANCE, [['仓库编码', 'warehouseCode'], ['规格编码', 'skuCode']]],
    [ORDER_LIST, [['客户编码', 'customerCodeSnapshot']]],
    [AGREEMENT_PRICE, [['客户编码', 'customerCode'], ['规格编码', 'skuCode']]],
    [CUSTOMER_TYPE_PRICE, [['客户类型编码', 'customerTypeCode'], ['规格编码', 'skuCode']]],
  ]) {
    for (const [title, dataIndex] of fields) {
      assert.match(page, new RegExp(`title: '${title}', dataIndex: '${dataIndex}'`));
      assert.match(page, new RegExp(`dataIndex: '${dataIndex}'[\\s\\S]{0,100}showFlag: false`));
    }
    assert.doesNotMatch(page, /class="scm-cell-stack/);
    assert.match(page, /TableOperator/);
  }
});

test('delivery routes expose route number beside its own column', () => {
  assert.match(ROUTE_LIST, /title: '线路名称', dataIndex: 'routeName'/);
  assert.match(ROUTE_LIST, /title: '线路编号', dataIndex: 'routeNo'/);
  assert.doesNotMatch(ROUTE_LIST, /class="scm-cell-stack/);
  assert.match(ROUTE_LIST, /:scroll="\{ x: tableScrollX \}"/);
});

test('inventory lists expose warehouse and spec codes separately with dynamic TableOperator widths', () => {
  for (const page of INVENTORY_CODE_LISTS) {
    assert.match(page, /dataIndex: 'warehouseCode'[\s\S]{0,100}showFlag: false/);
    assert.match(page, /dataIndex: 'warehouseName'/);
    assert.match(page, /dataIndex === 'warehouseCode'[\s\S]{0,120}class="scm-mono"/);
    assert.match(page, /const scrollX = computed\(/);
    assert.match(page, /:scroll="\{ x: scrollX \}"/);
    assert.match(page, /TableOperator/);
  }
  for (const page of INVENTORY_CODE_LISTS.slice(0, 3)) {
    assert.match(page, /dataIndex: 'skuCode'[\s\S]{0,100}showFlag: false/);
    assert.match(page, /dataIndex === 'skuCode'[\s\S]{0,120}class="scm-mono"/);
    assert.match(page, /dataIndex: 'skuName'/);
  }
});
