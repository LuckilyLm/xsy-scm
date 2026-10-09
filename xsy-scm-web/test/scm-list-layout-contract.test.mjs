import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

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

test('TableOperator keeps newly introduced fields visible with existing user column settings', () => {
  // The merge starts from the current page columns and defaults each to visible before matching saved keys.
  // New warehouseCode / skuCode keys therefore stay visible even when old settings contain only legacy columns.
  assert.match(COLUMN_MERGE, /fontColumn\.showFlag = true/);
  assert.match(COLUMN_MERGE, /userTableColumnMap\.get\(fontColumn\.columnKey\)/);
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
