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
