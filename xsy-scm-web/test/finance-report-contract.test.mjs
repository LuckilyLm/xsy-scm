/**
 * Finance R0 报表中心的**契约**单测（新增文件，仿 `w6-inventory-contract.test.mjs` 的源码扫描取向）。
 *
 * 这里守的是「只有跨文件同时成立才有意义」的不变量，靠肉眼验收发现不了：
 *
 * 1. 接口清单与后端 `/scm/report` 的固定契约逐条对齐，且**没有任何写端点**
 *    （R0 是只读报表，一个多余的 create 就是第二个写入口）；
 * 2. 导出必须走 `postDownload`（文件名来自 `Content-Disposition`，前端不硬编码、不拼 Blob），
 *    并且导出请求体不带分页——带分页会让人以为导的是当前页；
 * 3. 权限码与后端逐字一致，查询按钮带权限、重置按钮不带；
 * 4. 成本列与「当前库存价值」Tab 受 `scm:report:cost:query` 控制；
 * 5. 指标名就是口径名：出现「营业收入 / 应收 / 应付 / 毛利」这类 R0 无事实的命名即为缺陷；
 * 6. 「待入库」不提供入库写入口，只有回原收货单的链接；
 * 7. 金额与数量不在前端做数值运算（`Number(` / `toFixed` / `parseFloat` 不得出现在页面里），
 *    方向也不得有第二份 IN / OUT 清单；
 * 8. 表格 id（Playwright 用的 DOM id 与列配置用的数字 id）不得重复——
 *    重复会让列配置互相覆盖，且同一 DOM 里两个同 id 会让用例选错表。
 *
 * 扫描前剥掉注释：这些文件里大量出现「不得叫营业收入」这类反例说明，
 * 不剥注释就会把纪律文档本身判成违规。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, existsSync} from 'node:fs';

/** 读源码并剥掉注释（先剥 HTML 注释再剥块注释，顺序不能反）。 */
function code(relative) {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '');
}

function raw(relative) {
  return readFileSync(new URL(relative, import.meta.url), 'utf8');
}

const REPORT_DIR = '../src/views/business/scm/report/';
const PAGES = {
  overview: code(`${REPORT_DIR}report-overview-list.vue`),
  sales: code(`${REPORT_DIR}report-sales-list.vue`),
  purchase: code(`${REPORT_DIR}report-purchase-list.vue`),
  receipt: code(`${REPORT_DIR}report-receipt-list.vue`),
  inventory: code(`${REPORT_DIR}report-inventory-list.vue`),
};
const API = code('../src/api/business/scm/report-api.ts');
const CONST = code('../src/constants/business/scm/report-const.ts');
const ALL_PAGES = Object.values(PAGES).join('\n');

// ------------------------------------------------------------------
// 布局与文件存在性
// ------------------------------------------------------------------

test('报表页是扁平 *-list.vue（与库存 / 采购域同构），不是 plan 里的 overview/index.vue', () => {
  for (const name of [
    'report-overview-list.vue',
    'report-sales-list.vue',
    'report-purchase-list.vue',
    'report-receipt-list.vue',
    'report-inventory-list.vue',
    'report-types.ts',
    'report-model.ts',
    'report-errors.ts',
  ]) {
    assert.ok(existsSync(new URL(`${REPORT_DIR}${name}`, import.meta.url)), `缺少 ${name}`);
  }
  assert.ok(!existsSync(new URL(`${REPORT_DIR}overview/index.vue`, import.meta.url)), '不应建 index.vue 目录式页面');
});

// ------------------------------------------------------------------
// 接口契约
// ------------------------------------------------------------------

const CONTRACT_PATHS = [
  '/overview',
  '/overview/trend',
  '/overview/daily',
  '/sales/product',
  '/sales/category',
  '/sales/customer',
  '/sales/seller',
  '/sales/item/query',
  '/sales/product/top',
  '/sales/category/top',
  '/sales/customer/top',
  '/sales/product/export',
  '/sales/customer/export',
  '/sales/item/export',
  '/purchase/overview',
  '/purchase/product',
  '/purchase/supplier',
  '/purchase/purchaser',
  '/purchase/item/query',
  '/purchase/supplier/top',
  '/purchase/price-trend',
  '/purchase/product/export',
  '/purchase/supplier/export',
  '/purchase/item/export',
  '/receipt/query',
  '/inbound/query',
  '/pending-putaway/query',
  '/receipt/export',
  '/inbound/export',
  '/inventory/movement/query',
  '/inventory/loss/summary',
  '/inventory/loss/query',
  '/inventory/value/query',
  '/inventory/flow-summary/query',
  '/inventory/movement/export',
  '/inventory/loss/export',
  '/inventory/value/export',
];

test('后端约定的 37 个端点逐条存在，且都挂在 /scm/report 下', () => {
  assert.equal(CONTRACT_PATHS.length, 37, '清单本身必须与计划 §30 + 本次固定契约同数');
  assert.match(API, /const BASE = '\/scm\/report'/);
  for (const path of CONTRACT_PATHS) {
    assert.ok(API.includes('${BASE}' + path), `缺少端点 ${path}`);
  }
});

test('报表 API 没有任何写端点与第二套上传', () => {
  for (const forbidden of ['create', 'add', 'update', 'delete', 'save', 'putaway', 'confirm']) {
    assert.ok(!new RegExp(`\\b${forbidden}\\s*:`).test(API), `只读 API 里出现了 ${forbidden}`);
  }
  assert.ok(!/putRequest|deleteRequest/.test(API), '不得出现写请求方法');
});

test('导出统一走 postDownload，不自己拼 Blob、不硬编码文件名', () => {
  const exportCount = (API.match(/postDownload\(/g) ?? []).length;
  assert.equal(exportCount, 11, '导出端点数与契约一致（销售 3 + 采购 3 + 收货 2 + 库存 3）');
  assert.ok(!/new Blob|createObjectURL|\.xlsx'/.test(API), '文件名与下载由 postDownload 负责');
});

test('页面导出用当前筛选，且不带分页参数', () => {
  // 有导出的四张页面：exportQuery() 只由日期区间 + 共享筛选装配，不接 tab。
  // 概览页按计划 §30 没有导出端点，所以它反过来必须「没有」导出装配。
  assert.ok(!/function exportQuery\(\)/.test(PAGES.overview), '概览页不该有导出装配（计划里没有概览导出）');
  for (const [name, source] of Object.entries(PAGES)) {
    if (name === 'overview') continue;
    const body = source.match(/function exportQuery\([\s\S]*?\n\s*\}/)?.[0];
    assert.ok(body, `${name} 页应有一个 exportQuery()`);
    assert.match(body, /buildReportQuery</, `${name} 页的导出必须复用同一个装配函数`);
    assert.ok(!/pageNum/.test(body), `${name} 页的导出装配不得带分页`);
  }
});

// ------------------------------------------------------------------
// 权限
// ------------------------------------------------------------------

test('六个权限码逐字正确', () => {
  for (const perm of [
    'scm:report:overview:query',
    'scm:report:sales:query',
    'scm:report:purchase:query',
    'scm:report:inventory:query',
    'scm:report:cost:query',
    'scm:report:export',
  ]) {
    assert.ok(CONST.includes(`'${perm}'`), `常量里缺 ${perm}`);
  }
});

test('每页的查询按钮带本页 query 权限，重置按钮不带权限', () => {
  const pagePerm = {
    overview: 'OVERVIEW_QUERY',
    sales: 'SALES_QUERY',
    purchase: 'PURCHASE_QUERY',
    receipt: 'PURCHASE_QUERY',
    inventory: 'INVENTORY_QUERY',
  };
  for (const [name, perm] of Object.entries(pagePerm)) {
    const source = PAGES[name];
    assert.match(source, new RegExp(`v-privilege="PERM\\.${perm}"[^>]*@click="onSearch"`), `${name} 页查询按钮缺权限`);
    const reset = source.match(/<a-button([^>]*)@click="resetQuery"/);
    assert.ok(reset, `${name} 页缺重置按钮`);
    assert.ok(!/v-privilege/.test(reset[1]), `${name} 页的重置按钮不应带权限`);
  }
  for (const [name, source] of Object.entries(PAGES)) {
    if (name === 'overview') continue;   // 计划 §30 里没有概览导出
    assert.match(source, /v-privilege="PERM\.EXPORT"/, '导出按钮挂在 export 权限上');
  }
});

test('成本列与价值 Tab 受 cost 权限控制', () => {
  assert.match(PAGES.inventory, /filterCostColumns/, '库存页必须裁成本列');
  assert.match(PAGES.inventory, /v-if="canViewCost"[\s\S]{0,400}?key="value"/, '无成本权限时价值 Tab 必须不渲染');
  assert.match(PAGES.purchase, /filterCostColumns/);
  assert.match(PAGES.receipt, /filterCostColumns/);
  assert.match(PAGES.overview, /filterCostColumns/);
});

// ------------------------------------------------------------------
// 命名即口径
// ------------------------------------------------------------------

const REQUIRED_LABELS = [
  '已确认订单数',
  '下单客户数',
  '已确认订单金额',
  '已完成退款金额',
  '已提交采购金额',
  '采购入库成本金额',
  '当前库存账面金额',
  '收货参考金额',
  '入库单位成本',
  '损耗总成本金额',
  '期内净变动量',
];

test('计划里的指标名逐字出现，没有被同义词替换掉', () => {
  for (const label of REQUIRED_LABELS) {
    assert.ok(ALL_PAGES.includes(label), `页面里找不到指标名「${label}」`);
  }
});

test('没有把 R0 无事实的口径写进标签（title / label / tab）', () => {
  const labels = [
    ...ALL_PAGES.matchAll(/title:\s*'([^']*)'/g),
    ...ALL_PAGES.matchAll(/label:\s*'([^']*)'/g),
    ...ALL_PAGES.matchAll(/label="([^"]*)"/g),
    ...ALL_PAGES.matchAll(/tab="([^"]*)"/g),
    ...ALL_PAGES.matchAll(/title="([^"]*)"/g),
  ].map((match) => match[1]);
  assert.ok(labels.length > 80, '标签样本太少说明扫描方式失效');
  for (const forbidden of ['营业收入', '销售收入', '实收金额', '应收', '应付', '毛利', '资金流水', '已收款', '待收款']) {
    const hit = labels.filter((label) => label.includes(forbidden));
    assert.deepEqual(hit, [], `标签里出现了 R0 无事实的口径：${forbidden}`);
  }
});

test('「当前库存账面金额」带截至时点并被标记为时点值', () => {
  assert.match(PAGES.overview, /截至/, '概览卡必须显示截至时间');
  assert.match(PAGES.overview, /current-point|currentPoint/, '概览卡要标记这是当前时点值');
  assert.match(PAGES.overview, /snapshotAt/);
});

// ------------------------------------------------------------------
// 只读边界
// ------------------------------------------------------------------

test('待入库 Tab 只有回原收货单的链接，没有入库写入口', () => {
  assert.match(PAGES.receipt, /查看原收货单/);
  // 按「可点的动作」判，不按词判：本页提示用户「请到采购收货页办理入库」是正确指引，
  // 按词匹配会把这条合规文案判成违规，于是纪律检查变成没人敢写说明文字。
  assert.ok(!/putaway\s*\(/.test(PAGES.receipt), '报表页不得调用入库写接口');
  assert.ok(!/<a-button[^>]*>[^<]*(确认入库|办理入库)/.test(PAGES.receipt), '报表页不得有入库按钮');
  // 不按「确认入库」这个词判：WAREHOUSE_CONFIRM 的正式名称就是「仓库确认入库」，
  // 待入库 Tab 的说明必须提到它，按词匹配会把合规说明判成违规。
  assert.ok(!/scm\/purchase\/receipt/.test(PAGES.receipt), '只跳采购收货页，不直接调它的写接口');
});

test('每个列表查询都有竞态保护，不靠隐藏按钮', () => {
  for (const [name, source] of Object.entries(PAGES)) {
    const guarded = /createTabLoader|createGuardedLoader|requestId/.test(source);
    assert.ok(guarded, `${name} 页缺竞态保护`);
  }
  assert.ok(!/disabled?Flag|disabled="!loading"/.test(ALL_PAGES));
});

test('金额与数量不在前端做数值运算', () => {
  for (const [name, source] of Object.entries(PAGES)) {
    assert.ok(!/\bNumber\(/.test(source), `${name} 页不该 Number() 后端定点数`);
    assert.ok(!/toFixed\(/.test(source), `${name} 页不该重排精度`);
    assert.ok(!/parseFloat\(/.test(source), `${name} 页不该把定点串转成浮点再算`);
  }
});

test('流水方向复用库存域的入方向集合，不另建 IN / OUT 清单', () => {
  assert.match(PAGES.inventory, /SCM_INVENTORY_MOVEMENT_INBOUND_TYPES/);
  assert.match(PAGES.inventory, /movementDirection\(/);
  // 页面里再出现一份流水类型字符串数组就是第二份真相
  // 只有把枚举名当**数组元素**写死才算第二份清单；提示文案里提到 PURCHASE_IN 不是
  assert.ok(!/=\s*\[\s*['"]PURCHASE_IN['"]/.test(ALL_PAGES), '页面不得内联流水类型清单');
  assert.ok(raw('../src/constants/business/scm/inventory-const.ts').includes('SCM_INVENTORY_MOVEMENT_INBOUND_TYPES'));
});

test('报表枚举不复制库存 / 采购既有枚举的值', () => {
  const constRaw = raw('../src/constants/business/scm/report-const.ts');
  // report-const 只声明报表侧特有的损耗子集，其余一律复用
  assert.ok(!constRaw.includes("STOCKTAKE_GAIN: {value: 'STOCKTAKE_GAIN'"), '不应复制盘盈枚举');
  assert.ok(!constRaw.includes("WAREHOUSE_CONFIRM: {value: 'WAREHOUSE_CONFIRM'"), '收货模式应复用 purchase-const');
  assert.match(code(`${REPORT_DIR}report-receipt-list.vue`), /SCM_RECEIPT_MODE_ENUM/);
});

// ------------------------------------------------------------------
// 表格 id 唯一性
// ------------------------------------------------------------------

test('SCM_REPORT_TABLE_ID 的 DOM id 不重复', () => {
  const values = [...CONST.matchAll(/:\s*'(scm-report-[a-z-]+)'/g)].map((match) => match[1]);
  assert.ok(values.length >= 18, 'DOM id 样本太少');
  assert.equal(new Set(values).size, values.length, `存在重复 DOM id：${values}`);
});

test('列配置用的数字 tableId 全局唯一', () => {
  const source = code('../src/constants/support/table-id-const.ts');
  const values = [...source.matchAll(/SCM_[A-Z_]+:\s*(\d+)/g)].map((match) => Number(match[1]));
  assert.ok(values.length >= 40, 'tableId 样本太少');
  const dupes = values.filter((value, index) => values.indexOf(value) !== index);
  assert.deepEqual([...new Set(dupes)], [], `TABLE_ID_CONST.BUSINESS 里有重复数字 id：${dupes}`);
});

test('五个报表页各自的表格都绑了 DOM id 与 TableOperator', () => {
  for (const [name, source] of Object.entries(PAGES)) {
    assert.match(source, /SCM_REPORT_TABLE_ID\./, `${name} 页缺 Playwright 用的表格 id`);
    assert.match(source, /TableOperator/, `${name} 页缺列设置入口`);
  }
});

// ------------------------------------------------------------------
// 共享错误处理
// ------------------------------------------------------------------

test('报表错误码有可执行文案且未吞掉后端消息', () => {
  const errors = code(`${REPORT_DIR}report-errors.ts`);
  for (const code of [41110, 41111, 41112]) {
    assert.ok(errors.includes(`${code}:`), `缺错误码 ${code} 的映射`);
  }
  assert.match(errors, /e\?\.msg \?\? e\?\.message/, '未登记的码要回落到后端消息');
});

test('每页都有 error 横幅与重试入口', () => {
  for (const [name, source] of Object.entries(PAGES)) {
    assert.match(source, /type="error"/, `${name} 页缺错误提示`);
    assert.match(source, /重试/, `${name} 页缺重试入口`);
  }
});

test('空态与合计纪律：数量列不做跨单位合计', () => {
  assert.match(PAGES.inventory, /期内净变动量/);
  assert.match(PAGES.purchase, /inboundQuantityText/, '采购入库数量按单位分组的文本列必须存在');
  assert.match(PAGES.receipt, /quantityText/, '待入库的收货数量是分组文本，不是单个数字');
  assert.ok(!/summary:\s*\(\)/.test(ALL_PAGES), '没有后端合计时不放前端自算的合计行');
});
