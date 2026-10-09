import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

function source(relative) {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '');
}

const TREND = source('../src/views/system/home/components/business-trend.vue');
const HEADER = source('../src/views/system/home/home-header.vue');
const RANKING = source('../src/views/system/home/components/ranking-card.vue');
const NOTICE = source('../src/views/system/home/home-notice.vue');
const HOME = source('../src/views/system/home/index.vue');
const METRIC_CARDS = source('../src/views/system/home/components/metric-cards.vue');
const REGION_ERROR = source('../src/views/system/home/components/region-error.vue');
const USE_ECHARTS = source('../src/views/business/scm/screen/composables/use-echarts.ts');

test('home trend distinguishes valid zero series from unavailable trend data', () => {
  assert.match(TREND, /const hasData = computed\(/);
  assert.match(TREND, /const allZero = computed\(/);
  assert.match(TREND, /primarySeries\.length !== trend\.dates\.length/);
  assert.match(TREND, /secondarySeries\.length !== trend\.dates\.length/);
  assert.match(TREND, /Number\.isFinite\(Number\(value\)\)[\s\S]{0,60}Number\(value\) === 0/);
  assert.match(TREND, /description="暂无趋势数据"/);
  assert.match(TREND, /:description="allZeroDescription"/);
  assert.match(TREND, /sales: '暂无成交记录'[\s\S]{0,100}purchase: '暂无采购记录'[\s\S]{0,100}inventory: '暂无库存流转记录'/);
  assert.match(TREND, /height: 264px/);
  assert.match(TREND, /@media \(max-width: 767px\)[\s\S]{0,240}height: 232px/);
});

test('home welcome and ranking empty states give real data more room', () => {
  assert.match(HEADER, /min-height: 148px/);
  assert.doesNotMatch(HEADER, /home-welcome__brand|home-welcome__slogan|xsy-logo-mark\.png/);
  assert.match(RANKING, /\.home-rank__empty[\s\S]{0,160}min-height: 200px/);
  assert.match(RANKING, /\.home-rank__list[\s\S]{0,160}min-height: 300px/);
  // Permission-based card visibility and responsive columns remain data-driven.
  assert.match(HOME, /v-if="canRanking"/);
  assert.match(HOME, /v-if="canHealth"/);
  assert.match(METRIC_CARDS, /grid-template-columns: repeat\(var\(--kpi-columns\)/);
});

test('blank notice titles have a safe label and preserve the notice source', () => {
  assert.match(NOTICE, /function noticeTitle\(item: HomeNotice\): string/);
  assert.match(NOTICE, /item\.title\?\.trim\(\) \|\| '未命名通知'/);
  assert.match(NOTICE, /noticeApi\.queryEmployeeNotice/);
  assert.match(NOTICE, /:title="noticeTitle\(item\)"/);
  assert.match(NOTICE, /\{\{ noticeTitle\(item\) \}\}/);
});

test('home request failures appear centered in their own region with retry', () => {
  assert.match(REGION_ERROR, /<a-empty[\s\S]{0,180}role="alert"/);
  assert.match(REGION_ERROR, /justify-content:\s*center/);
  assert.match(REGION_ERROR, /<a-button size="small" @click="emit\('retry'\)">重试<\/a-button>/);
  assert.doesNotMatch(REGION_ERROR, /<a-alert/);
  for (const component of [TREND, RANKING, NOTICE, REGION_ERROR]) {
    assert.match(component, /retry|重试/);
  }
});

/*
 * 「切换了但图不变」回归锁。
 *
 * 首页趋势卡的容器是 v-show 控制的：点指标时 active 先变、data 还是旧响应，
 * hasData 立即转 false，容器短暂 0×0。此刻 render() 已经带着新数据跑过一次，
 * 但 useEcharts.ensure() 见尺寸为 0 就把配置丢进了 pending。
 * 若 ResizeObserver 在元素恢复尺寸时只调 resize()，画布就会用旧配置重绘 —— 五个指标
 * 画出来一模一样。修复点是在有实例时也把 pending 重新 setOption 一次。
 */
test('chart composable replays the latest option when a hidden container becomes visible again', () => {
  // 尺寸为 0 时必须早退，不能进 resize 分支（否则会在隐藏态白跑一次重绘）
  assert.match(
    USE_ECHARTS,
    /new ResizeObserver\(\(\) => \{[\s\S]{0,400}if \(!el\.clientWidth \|\| !el\.clientHeight\) \{[\s\S]{0,80}return;/
  );
  // 已有实例 + 有 pending ⇒ 必须重新应用配置，而不是只 resize()
  assert.match(USE_ECHARTS, /if \(pending\) \{[\s\S]{0,80}chart\.setOption\(pending, true\);/);
  // setOption 必须同时记住 pending，供上面那条分支消费
  assert.match(USE_ECHARTS, /function setOption\(option: ChartOption, notMerge = true\) \{[\s\S]{0,120}pending = option;/);
});
