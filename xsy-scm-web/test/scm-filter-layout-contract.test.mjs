import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

function source(relative) {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '');
}

const FILTER_STYLE = source('../src/theme/scm/filter.less');
const SMART_ADMIN_STYLE = source('../src/theme/smart-admin.less');
const PAGES = [
  source('../src/views/business/scm/finance/finance-payable-list.vue'),
  source('../src/views/business/scm/purchase/purchase-demand-list.vue'),
  source('../src/views/business/scm/inventory/inventory-warning-threshold-list.vue'),
];

test('SCM filter fields wrap with aligned controls and separate actions', () => {
  assert.match(FILTER_STYLE, /\.scm-filter-bar\s*\{[\s\S]*?align-items:\s*center[\s\S]*?flex-wrap:\s*wrap[\s\S]*?gap:\s*8px 12px/);
  assert.match(FILTER_STYLE, /\.scm-filter-fields\s*\{[\s\S]*?display:\s*flex[\s\S]*?flex-wrap:\s*wrap/);
  assert.match(FILTER_STYLE, /\.scm-filter-actions\s*\{[\s\S]*?display:\s*flex[\s\S]*?gap:\s*8px[\s\S]*?margin-left:\s*auto/);
  assert.match(FILTER_STYLE, /\.scm-filter-range[\s\S]*?\.ant-picker\s*\{[\s\S]*?min-width:\s*0/);
  assert.match(FILTER_STYLE, /@media\s*\(max-width:\s*768px\)[\s\S]*?\.scm-filter-fields\s*\{[\s\S]*?flex:\s*1 1 100%/);
});

test('SCM toolbars use flex gaps while SmartAdmin keeps its separate query rules', () => {
  assert.match(FILTER_STYLE, /\.scm-table-toolbar\.smart-table-btn-block\s*\{[\s\S]*?flex-wrap:\s*wrap/);
  assert.match(FILTER_STYLE, /\.smart-table-setting-block\s*\{[\s\S]*?display:\s*flex[\s\S]*?gap:\s*8px[\s\S]*?float:\s*none[\s\S]*?margin-left:\s*auto/);
  assert.match(FILTER_STYLE, /\.smart-table-operate-block[\s\S]*?gap:\s*8px[\s\S]*?margin-right:\s*0/);
  assert.doesNotMatch(SMART_ADMIN_STYLE, /scm-filter-bar|scm-table-toolbar/);
  assert.match(SMART_ADMIN_STYLE, /\.smart-query-form\.ant-form-inline/);
});

test('payables, purchase demand and thresholds share the filter and toolbar layout', () => {
  for (const page of PAGES) {
    assert.match(page, /class="scm-filter-bar"/);
    assert.match(page, /class="scm-filter-fields"/);
    assert.match(page, /class="scm-filter-actions"/);
    assert.match(page, /class="smart-table-btn-block scm-table-toolbar"/);
    assert.doesNotMatch(page, /<a-button-group>/);
    assert.match(page, /查询/);
    assert.match(page, /重置/);
  }
});
