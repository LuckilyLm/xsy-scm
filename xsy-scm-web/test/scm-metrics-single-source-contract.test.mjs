/*
 * 经营指标单一来源契约：同一个业务指标只能有一处 SQL 定义。
 *
 * 为什么需要这条门禁：首页、大屏、报表都要显示「今日销售额」。各自写一份 SQL 时，两份都跑得通、
 * 都不报错，只是数字慢慢不一样 —— 而用户会先失去对数字的信任，再去找人核对。这类漂移不会触发任何
 * 构建期或运行期错误，只能靠把「定义处」钉死来防。
 *
 * 本文件只做源码级检查（快、无需数据库）；数值层面的守卫在
 * ScmBusinessMetricsConsistencyPgIT（同区间两端取值必须相等）。
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const ROOT = path.resolve(fileURLToPath(new URL('../../', import.meta.url)));
const MAPPERS = path.join(ROOT, 'xsy-scm-server/sa-admin/src/main/resources/mapper/scm');
const METRICS_MAPPER = path.join(MAPPERS, 'metrics/ScmBusinessMetricsMapper.xml');
const SCREEN_MAPPER = path.join(MAPPERS, 'screen/ScreenDataMapper.xml');
const DASHBOARD_SRC = path.join(ROOT, 'xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/dashboard');

const read = (file) => readFileSync(file, 'utf8');

/** 取出一个 mapper 里全部 <select id="..."> 的 id。 */
const selectIds = (xml) => [...xml.matchAll(/<select\s+id="([^"]+)"/g)].map((m) => m[1]);

/** 取出一个 mapper 里指定 <sql id="..."> 的完整片段（含标签）。 */
function sqlFragment(xml, id) {
  const match = new RegExp(`<sql id="${id}">[\\s\\S]*?</sql>`).exec(xml);
  assert.ok(match, `缺少 sql 片段 ${id}`);
  return match[0];
}

/** 递归收集目录下所有 .java / .xml 文件。 */
function sourceFiles(dir) {
  const found = [];
  for (const entry of readdirSync(dir)) {
    const full = path.join(dir, entry);
    if (statSync(full).isDirectory()) found.push(...sourceFiles(full));
    else if (/\.(java|xml)$/.test(entry)) found.push(full);
  }
  return found;
}

// 迁入 metrics 的指标：迁完就必须在这里，缺一个都说明「搬了一半」。
const MOVED_METRICS = [
  'countOrdersByConfirmedAt',
  'sumSettlementAmountByConfirmedAt',
  'sumOrderedAmountByCreatedAt',
  'countCustomersWithOrdersByConfirmedAt',
  'topCustomersByConfirmedAt',
  'topProductsByConfirmedAt',
  'countTotalOrders',
  'sumTotalSettlementAmount',
  'countCustomers',
  'countSuppliers',
  'countSkus',
  'sumInventoryQuantity',
  'countInventorySkus',
  'countEnabledWarehouses',
  'countMovementsByTypeAndRange',
  'inventoryHealthRows',
  'countPurchaseOrdersByCreatedAt',
  'sumPurchaseAmountByCreatedAt',
  'countTotalPurchaseOrders',
  'sumTotalPurchaseAmount',
  'countReceipts',
  'countSuppliersWithOrdersByCreatedAt',
  'trendByDay',
];

test('经营类指标全部定义在 metrics mapper，迁一半会留下两个口径', () => {
  const ids = new Set(selectIds(read(METRICS_MAPPER)));
  const missing = MOVED_METRICS.filter((id) => !ids.has(id));
  assert.deepEqual(missing, [], `这些指标必须定义在 ScmBusinessMetricsMapper.xml：${missing.join(', ')}`);
});

test('大屏 mapper 不再重复定义经营类指标', () => {
  const xml = read(SCREEN_MAPPER);
  const ids = selectIds(xml);
  const duplicated = MOVED_METRICS.filter((id) => ids.includes(id));
  assert.deepEqual(duplicated, [], `这些指标已迁入 metrics，大屏不得再有一份：${duplicated.join(', ')}`);

  // 销售金额列只允许出现在 metrics：出现在这里就说明有人又抄了一份销售额口径。
  for (const column of ['settlement_total_amount', 'ordered_total_amount']) {
    assert.ok(
      !xml.includes(column),
      `大屏 mapper 不应再出现 ${column}（销售额 / 下单金额的唯一口径在 metrics）`,
    );
  }
});

test('两处共用的数据范围谓词逐字一致，避免两边各自漂移', () => {
  const metrics = read(METRICS_MAPPER);
  const screen = read(SCREEN_MAPPER);
  // 这三段在两边都还要用：metrics 的经营类指标用，大屏的仓库分布 / 网络节点 / 地理分布用。
  for (const id of ['scopeC', 'scopeB', 'scopeW']) {
    assert.equal(
      sqlFragment(screen, id),
      sqlFragment(metrics, id),
      `${id} 在两份 mapper 里不一致：范围谓词抄两份就一定会有一边先改，两边先改的那次不会报错`,
    );
  }
});

test('dashboard 模块不得自己写经营统计 SQL', () => {
  const offenders = [];
  for (const file of sourceFiles(DASHBOARD_SRC)) {
    const text = read(file);
    if (/\b(sales_order|purchase_order|inventory_balance)\b/.test(text)) {
      offenders.push(path.relative(ROOT, file));
    }
  }
  assert.deepEqual(
    offenders,
    [],
    `dashboard 只做装配与权限裁剪，指标一律向 ScmBusinessMetricsService 取：${offenders.join(', ')}`,
  );
});
