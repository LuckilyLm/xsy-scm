/*
 * 配送线路详情页契约：地图里程语义、页签权限归一、返回列表带筛选。
 *
 * 这三处都是「错了也不报错」的行为，只能读源码钉住：缺定位时给出总里程会被读成整线统计；
 * 从 URL 改页签参数能绕过排线权限；返回按钮丢掉列表筛选会让用户重筛一遍。
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const PANEL = new URL('../src/views/business/scm/delivery/components/route-map-panel.vue', import.meta.url);
const PAGE = new URL('../src/views/business/scm/delivery/route-detail-page.vue', import.meta.url);
const LIST = new URL('../src/views/business/scm/delivery/route-list.vue', import.meta.url);

const code = (url) => readFileSync(url, 'utf8');

test('部分停靠点未定位时不显示整线里程，只说明展示范围', () => {
  const panel = code(PANEL);
  // 汇总块必须先过 allLocated：缺定位时算路只覆盖已定位的连续段，累加出来的不是整线里程。
  assert.match(
    panel,
    /<template v-if="allLocated && routeStatus\.state === 'ready' && drivingResult">/,
    '路程 / 预计必须挂在 allLocated 上，否则缺定位的线路会被读成整线统计',
  );
  assert.match(panel, /仅显示已定位路段，不作为整线里程/);
  assert.doesNotMatch(
    panel,
    /<template v-if="routeStatus\.state === 'ready' && drivingResult">/,
    '不带 allLocated 的汇总块会让缺定位的线路也显示整线里程',
  );
});

test('页签归一只有一处：白名单之外的回落基础信息，辅助排线还要过查询权', () => {
  const page = code(PAGE);
  assert.match(page, /function normalizeTab\(value: unknown\): string \{/);
  assert.match(page, /next === 'plan' && !hasPerm\(DELIVERY_PERM\.PLAN_QUERY\)/);
  // 声明一处 + 两个入口各调一次；各自判一遍就会漏，曾经 syncFromRoute 漏掉权限校验。
  const uses = page.match(/normalizeTab\(/g) ?? [];
  assert.ok(uses.length >= 3, `normalizeTab 应被声明一次并至少调用两次，实际出现 ${uses.length} 次`);
  assert.doesNotMatch(page, /tab\.value = TAB_KEYS\.includes\(/);
});

test('详情返回列表时把筛选与页码原样带回，不走浏览器历史回退', () => {
  const page = code(PAGE);
  assert.match(
    page,
    /function backToList\(\) \{[\s\S]*?delete filters\.tab;[\s\S]*?path: '\/delivery\/routes', query: filters/,
  );
  assert.doesNotMatch(page, /router\.back\(\)/, '直接打开详情时上一页可能是站外，不能依赖浏览器历史');

  const list = code(LIST);
  // 带出去与带回来共用同一份键清单
  assert.match(list, /function listFilterParams\(\): Record<string, string> \{/);
  assert.match(list, /for \(const key of Object\.keys\(ROUTE_DEEP_LINK\)\)/);
  assert.match(list, /query: \{tab: targetTab, \.\.\.listFilterParams\(\)\}/);
  for (const key of ['status', 'keyword', 'deliveryDate', 'warehouseId', 'driverId', 'vehicleId', 'pageNum', 'pageSize']) {
    assert.match(list, new RegExp(`^\\s*${key}: `, 'm'), `ROUTE_DEEP_LINK 缺少可恢复的键 ${key}`);
  }
});

test('列表把 URL 当作已执行查询状态：按钮只改路由，watcher 只消费路由', () => {
  const list = code(LIST);
  assert.match(list, /function applyQuery\(\) \{[\s\S]*?router\.replace\(\{path: route\.path, query: params\}\)/);
  // 查询 / 重置 / 翻页都只写 URL，不再直接 load()；watcher 是唯一的查询发起方。
  assert.match(list, /function search\(\) \{\s*query\.pageNum = 1;\s*applyQuery\(\);/);
  assert.match(list, /function reset\(\) \{\s*clearQuery\(\);\s*applyQuery\(\);/);
  assert.match(list, /@change="applyQuery"/);
});
