import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const tableStyle = readFileSync(new URL('../src/theme/scm/table.less', import.meta.url), 'utf8');
const supplierPage = readFileSync(new URL('../src/views/business/scm/supplier/supplier-list.vue', import.meta.url), 'utf8');
const payablePage = readFileSync(new URL('../src/views/business/scm/finance/finance-payable-list.vue', import.meta.url), 'utf8');

function column(source, dataIndex) {
  const escapedIndex = dataIndex.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const match = new RegExp(`\\{[^{}]*dataIndex:\\s*'${escapedIndex}'[^{}]*\\}`).exec(source);
  assert.ok(match, `缺少 dataIndex=${dataIndex} 的表格列`);
  return match[0];
}

test('全局表格样式不覆盖列水平对齐，并让数据单元垂直居中', () => {
  assert.doesNotMatch(tableStyle, /\.ant-table-thead\s*>\s*tr\s*>\s*th\s*\{[^}]*text-align:\s*center\s*!important/s);
  assert.match(tableStyle, /\.ant-table-tbody\s*>\s*tr\s*>\s*td\s*\{[^}]*vertical-align:\s*middle/s);
});

test('供应商与应付样板按字段语义设置同轴对齐', () => {
  assert.match(column(supplierPage, 'name'), /align:\s*'left'/);
  assert.match(column(supplierPage, 'skuCount'), /align:\s*'right'/);
  assert.match(column(supplierPage, 'status'), /align:\s*'center'/);
  assert.match(column(supplierPage, 'action'), /align:\s*'center'/);

  assert.match(column(payablePage, 'payableNo'), /align:\s*'left'/);
  assert.match(column(payablePage, 'supplierName'), /align:\s*'left'/);
  assert.match(column(payablePage, 'amount'), /align:\s*'right'/);
  assert.match(column(payablePage, 'writtenOffAmount'), /align:\s*'right'/);
  assert.match(column(payablePage, 'settleState'), /align:\s*'center'/);
  assert.match(column(payablePage, 'action'), /align:\s*'center'/);
  assert.match(column(payablePage, 'redAmount'), /align:\s*'right'/);
});

test('可排序表头让标题与排序图标作为一组沿列对齐轴排列', () => {
  assert.match(tableStyle, /\.ant-table-column-sorters\s*\{\s*justify-content:\s*flex-start/s);
  assert.match(tableStyle, /\[style\*='text-align: right'\].*?\.ant-table-column-sorters\s*\{\s*justify-content:\s*flex-end/s);
  assert.match(tableStyle, /\[style\*='text-align: center'\].*?\.ant-table-column-sorters\s*\{\s*justify-content:\s*center/s);
  assert.match(tableStyle, /\.ant-table-column-title\s*\{\s*flex:\s*0 1 auto/s);
});
