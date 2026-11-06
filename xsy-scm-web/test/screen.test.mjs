import test from 'node:test';
import assert from 'node:assert/strict';
import {escapeScreenHtml, formatAmount, formatScreenCompact, toChartValue} from '../src/views/business/scm/screen/format.ts';
import {getScreenLayout} from '../src/views/business/scm/screen/composables/use-screen-scale.ts';

test('大屏金额保留完整小数，窄列缩写正确进位', () => {
  const cases = [
    [0, '0.00', '0.00'],
    [9999, '9,999.00', '9,999.00'],
    [999999, '999,999.00', '100.00万'],
    [99999999.99, '99,999,999.99', '1.00亿'],
    [9999999999.99, '9,999,999,999.99', '100.00亿'],
  ];
  for (const [value, full, compact] of cases) {
    assert.equal(formatAmount(value), full);
    assert.equal(formatScreenCompact(full), compact);
  }
  assert.equal(formatScreenCompact('¥-12,345.67'), '¥-1.23万');
  assert.equal(formatScreenCompact('—'), '—');
});

test('趋势图保留零值并把缺失值留作断点', () => {
  for (const value of [0, '0', '0.00']) assert.equal(toChartValue(value), 0);
  for (const value of [null, undefined, '', '  ', '无数据', NaN, Infinity, false]) assert.equal(toChartValue(value), null);
  assert.equal(toChartValue('9999999999.99'), 9999999999.99);
});

test('大屏等比填满四种验收视口，超宽屏增加逻辑宽度', () => {
  for (const [width, height] of [
    [1366, 768],
    [1920, 1080],
    [2560, 1440],
    [3440, 1440],
  ]) {
    const layout = getScreenLayout(width, height);
    assert.ok(Math.abs(layout.width * layout.scale - width) < 0.001);
    assert.ok(Math.abs(layout.height * layout.scale - height) < 0.001);
  }
  assert.ok(getScreenLayout(1366, 768).scale > 768 / 1080);
  assert.equal(getScreenLayout(1920, 1080).scale, 1);
  assert.equal(getScreenLayout(3440, 1440).height, 1080);
  assert.ok(getScreenLayout(3440, 1440).width > 1920);
  assert.equal(getScreenLayout(0, 1080), null);
});

test('图表提示中的名称按原文字显示，不当作 HTML', () => {
  assert.equal(escapeScreenHtml('仓库<A&B> "北区"'), '仓库&lt;A&amp;B&gt; &quot;北区&quot;');
});
