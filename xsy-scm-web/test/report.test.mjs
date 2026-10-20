/**
 * 报表模块契约测试。
 *
 * 由 5 个独立测试文件按模块合并：每个 `{ … }` 块对应原来一份文件，
 * 块内 helper / 常量各自作用域隔离；只有 import 提到顶部并去重。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, existsSync} from 'node:fs';
import {enumDescText, createTabView, directionText, movementDirection, rangeOverLimitError, rangeFromQuery, buildReportQuery, countText, filterCostColumns, optionLabel, datePresets, incompleteCostHint, presetRange, dayRange, costText, defaultDateRange, textOrDash, enterTab, yesNoText, chartValue} from '../src/views/business/scm/report/report-model.ts';

// ---------------------------------------------------------------- 来源：finance-report-contract.test.mjs
{
  /**
   * 报表中心的契约测试（源码扫描取向）。
   *
   * 这里守的是「只有跨文件同时成立才有意义」的不变量，靠肉眼验收发现不了：
   *
   * 1. 接口清单与后端 `/scm/report` 的固定契约逐条对齐，且没有任何写端点
   *    （报表是只读的，一个多余的 create 就是第二个写入口）；
   * 2. 导出必须走 `postDownload`（文件名来自 `Content-Disposition`，前端不硬编码、不拼 Blob），
   *    并且导出请求体不带分页——带分页会让人以为导的是当前页；
   * 3. 权限码与后端逐字一致，查询按钮带权限、重置按钮不带；
   * 4. 成本列与「当前库存价值」Tab 受 `scm:report:cost:query` 控制；
   * 5. 指标名就是口径名：出现「营业收入 / 应收 / 应付 / 毛利」这类没有事实支撑的命名即为缺陷；
   * 6. 「待入库」不提供入库写入口，只有回原收货单的链接；
   * 7. 金额与数量不在前端做数值运算（`Number(` / `toFixed` / `parseFloat` 不得出现在页面里），
   *    方向也不得有第二份 IN / OUT 清单；
   * 8. 表格 id（Playwright 用的 DOM id 与列配置用的数字 id）不得重复——
   *    重复会让列配置互相覆盖，且同一 DOM 里两个同 id 会让用例选错表。
   *
   * 扫描前剥掉注释：这些文件里大量出现「不得叫营业收入」这类反例说明，
   * 不剥注释就会把纪律文档本身判成违规。
   */

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
  const REPORT_COMPONENT_DIR = `${REPORT_DIR}report-components/`;
  const PAGES = {
    overview: code(`${REPORT_DIR}report-overview-list.vue`),
    sales: code(`${REPORT_DIR}report-sales-list.vue`),
    purchase: code(`${REPORT_DIR}report-purchase-list.vue`),
    receipt: code(`${REPORT_DIR}report-receipt-list.vue`),
    inventory: code(`${REPORT_DIR}report-inventory-list.vue`),
  };
  const PAGE_COMPONENTS = {
    sales: [
      'sales-product-tab.vue', 'sales-category-tab.vue', 'sales-customer-tab.vue',
      'sales-seller-tab.vue', 'sales-item-tab.vue', 'sales-order-tab.vue',
    ].map(name => code(`${REPORT_COMPONENT_DIR}${name}`)),
    purchase: [
      'purchase-daily-report.vue', 'purchase-product-tab.vue', 'purchase-supplier-tab.vue',
      'purchase-purchaser-tab.vue', 'purchase-item-tab.vue', 'purchase-price-trend-tab.vue',
    ].map(name => code(`${REPORT_COMPONENT_DIR}${name}`)),
    inventory: ['inventory-loss-analysis-tab.vue'].map(name => code(`${REPORT_COMPONENT_DIR}${name}`)),
  };
  const FINANCE_PAGE = code(`${REPORT_DIR}report-finance-overview.vue`);
  const API = code('../src/api/business/scm/report-api.ts');
  const CONST = code('../src/constants/business/scm/report-const.ts');
  const ALL_PAGES = [...Object.values(PAGES), ...Object.values(PAGE_COMPONENTS).flat()].join('\n');

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
    '/finance/overview',
    '/finance/receivable/aging-free-detail',
    '/finance/payable/aging-free-detail',
    '/finance/overview/export',
    '/finance/receivable/aging-free-detail/export',
    '/finance/payable/aging-free-detail/export',
  ];

  test('后端约定的 43 个端点逐条存在，且都挂在 /scm/report 下', () => {
    assert.equal(CONTRACT_PATHS.length, 43, '清单本身必须与后端报表契约同数');
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
    // 端点清单逐个对过后端 controller 映射（ADM-01 的六个后补导出已在
    // ScmSalesReportController / ScmPurchaseReportController / ScmFinanceReportController 落地）。
    // 用清单而不是「数一个总数」：新增导出漏接后端时，这里会直接点名是哪一条。
    const exportPaths = [...API.matchAll(/postDownload\(`\$\{BASE\}([^`]+)`/g)].map((m) => m[1]).sort();
    assert.deepEqual(exportPaths, [
      '/finance/overview/export',
      '/finance/payable/aging-free-detail/export',
      '/finance/receivable/aging-free-detail/export',
      '/inbound/export',
      '/inventory/loss/export',
      '/inventory/movement/export',
      '/inventory/value/export',
      '/overview/daily/export',
      '/purchase/item/export',
      '/purchase/overview/export',
      '/purchase/price-trend/export',
      '/purchase/product/export',
      '/purchase/purchaser/export',
      '/purchase/supplier/export',
      '/receipt/export',
      '/sales/category/export',
      '/sales/customer/export',
      '/sales/item/export',
      '/sales/order/export',
      '/sales/product/export',
      '/sales/seller/export',
    ]);
    assert.equal(exportPaths.length, 21, '导出端点数与契约一致（概览 1 + 销售 6 + 采购 6 + 收货 2 + Finance 3 + 库存 3）');
    assert.ok(!/new Blob|createObjectURL|\.xlsx'/.test(API), '文件名与下载由 postDownload 负责');
  });

  test('页面导出用当前筛选，且不带分页参数', () => {
    // 有导出的四张页面：exportQuery() 只由日期区间 + 共享筛选装配，不接 tab。
    // 概览页没有导出端点，所以它反过来必须「没有」导出装配。
    assert.ok(!/function exportQuery\(\)/.test(PAGES.overview), '概览页不该有导出装配（没有概览导出端点）');
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

  test('七个权限码逐字正确', () => {
    for (const perm of [
      'scm:report:overview:query',
      'scm:report:sales:query',
      'scm:report:purchase:query',
      'scm:report:inventory:query',
      'scm:report:finance:query',
      'scm:report:cost:query',
      'scm:report:export',
    ]) {
      assert.ok(CONST.includes(`'${perm}'`), `常量里缺 ${perm}`);
    }
  });

  test('finance report overview exposes only the six fixed measures and read-only page actions', () => {
    for (const label of ['应收发生额', '应收已核销', '期末待收', '应付发生额', '应付已核销', '期末待付']) {
      assert.ok(FINANCE_PAGE.includes(label), `往来概览缺固定指标「${label}」`);
    }
    // 这两条语义压进指标名，而不是另写一条口径说明：报表模块不常驻解释（见 CONTRIBUTING.md SCM 界面规范）。
    assert.match(FINANCE_PAGE, /期末待收（含期初未结）/, '期末余额口径要说明不受起始日截断');
    assert.match(FINANCE_PAGE, /期末待付（含期初未结）/, '期末余额口径要说明不受起始日截断');
    assert.match(FINANCE_PAGE, /应收已核销（非实收）/, '已核销不得让人读成现金收付');
    assert.match(FINANCE_PAGE, /应付已核销（非实付）/, '已核销不得让人读成现金收付');
    assert.match(FINANCE_PAGE, /v-privilege="PERM\.FINANCE_QUERY"/, '页面查询必须受报表查询权限控制');
    assert.match(FINANCE_PAGE, /v-privilege="PERM\.EXPORT"/, '三个导出按钮必须受导出权限控制');
    assert.match(FINANCE_PAGE, /SCM_REPORT_TABLE_ID\.FINANCE_RECEIVABLE/);
    assert.match(FINANCE_PAGE, /SCM_REPORT_TABLE_ID\.FINANCE_PAYABLE/);
    assert.match(FINANCE_PAGE, /TableOperator/);
    assert.match(FINANCE_PAGE, /createGuardedLoader/);
    assert.match(FINANCE_PAGE, /createTabLoader/);
    assert.ok(!/\bNumber\(/.test(FINANCE_PAGE), '金额不在前端转数字计算');
    assert.ok(!/toFixed\(/.test(FINANCE_PAGE), '金额精度由 Finance 后端提供');
  });

  test('query and export requests share the report date range without exporting the current page', () => {
    assert.match(FINANCE_PAGE, /reportFinanceApi\.overviewExport\(overviewExportQuery\(\)\)/);
    assert.match(FINANCE_PAGE, /reportFinanceApi\.receivableExport\(detailExportQuery\(\)\)/);
    assert.match(FINANCE_PAGE, /reportFinanceApi\.payableExport\(detailExportQuery\(\)\)/);
    assert.match(FINANCE_PAGE, /const overviewExportQuery = \(\) => \(\{startDate: dateRange\.value\[0\], endDate: dateRange\.value\[1\]\}\)/);
    assert.match(FINANCE_PAGE, /const detailExportQuery = \(\) => \(\{\.\.\.overviewExportQuery\(\), keyword:/);
    assert.doesNotMatch(FINANCE_PAGE.match(/const detailExportQuery = [^\n]+/)?.[0] ?? '', /pageNum|pageSize/);
    for (const endpoint of [
      '/finance/overview',
      '/finance/receivable/aging-free-detail',
      '/finance/payable/aging-free-detail',
      '/finance/overview/export',
      '/finance/receivable/aging-free-detail/export',
      '/finance/payable/aging-free-detail/export',
    ]) {
      assert.ok(API.includes(`\${BASE}${endpoint}`), `Finance report API missing ${endpoint}`);
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
      if (name === 'overview') continue;   // 概览没有导出端点
      const panelSources = [source, ...(PAGE_COMPONENTS[name] ?? [])].join('\n');
      assert.match(panelSources, /v-privilege="(?:PERM|SCM_REPORT_PERMISSION)\.EXPORT"/, '导出按钮挂在 export 权限上');
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

  test('没有把无事实支撑的口径写进标签（title / label / tab）', () => {
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
      assert.deepEqual(hit, [], `标签里出现了无事实支撑的口径：${forbidden}`);
    }
  });

  test('「当前库存账面金额」带截至时点并被标记为时点值', () => {
    assert.match(PAGES.overview, /截至/, '概览卡必须显示截至时间');
    assert.match(PAGES.overview, /title:[ ]*'当前时点'/, '时点指标要单独成组并标注');
    assert.match(PAGES.overview, /point:[ ]*true/, '当前库存账面金额要标记为时点指标');
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
    // 只有把枚举名当数组元素写死才算第二份清单；提示文案里提到 PURCHASE_IN 不是
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
      const panelSources = [source, ...(PAGE_COMPONENTS[name] ?? [])].join('\n');
      assert.match(panelSources, /SCM_REPORT_TABLE_ID\./, `${name} 页缺 Playwright 用的表格 id`);
      assert.match(panelSources, /TableOperator/, `${name} 页缺列设置入口`);
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
}

// ---------------------------------------------------------------- 来源：finance-report-model.test.mjs
{
  /**
   * 报表中心的纯函数测试（`report-model.ts`）。
   *
   * 钉的是五个报表页共用、又最容易在改页面时被悄悄破坏的几条：
   * 1. 快捷日期：昨日 / 本周 / 上周 / 本月 / 上月的边界，周以周一为起点，
   *    「本周 / 本月」截至今天（截至周末 / 月末会把未来日子算进已发生）；
   * 2. 366 天跨度上限与空区间的处理；
   * 3. deep-link 还原：URL 是用户可手改的输入，半截或非法区间必须整体作废；
   * 4. 查询装配：清空的下拉不能以 `''` 发给后端（`@Pattern` 会拒整次查询），
   *    导出请求不带分页，日期区间不被共享筛选盖掉；
   * 5. Tab 隔离：切 Tab 只归目标 Tab 的页码，其它 Tab 的状态与对象身份都不变；
   * 6. 0 与 null 不合并：`null` → `—`，`"0.0000"` → `0.0000`，
   *    图表取数时 `null` 保持 `null` 而不是塌成 0；
   * 7. 成本门禁：无成本权限时金额再「真」也不能显示，列要被整列裁掉；
   * 8. 方向由枚举派生：入 / 出看调用方传入的集合，不在报表侧另判字符串。
   *
   * 加载方式与同目录其它测试一致：`node --experimental-strip-types` 直接吃 `.ts`，
   * 被测模块内只允许 type-only 的相对导入。
   */

  /** 2026-09-23 是星期三：本周周一是 09-21，上周一 09-14、上周日 09-20。 */
  const WEDNESDAY = '2026-09-23';

  // ------------------------------------------------------------------
  // 快捷日期
  // ------------------------------------------------------------------

  test('快捷日期五个预设的边界正确，周以周一为起点', () => {
      assert.deepEqual(presetRange('YESTERDAY', WEDNESDAY), ['2026-09-22', '2026-09-22']);
      assert.deepEqual(presetRange('THIS_WEEK', WEDNESDAY), ['2026-09-21', '2026-09-23']);
      assert.deepEqual(presetRange('LAST_WEEK', WEDNESDAY), ['2026-09-14', '2026-09-20']);
      assert.deepEqual(presetRange('THIS_MONTH', WEDNESDAY), ['2026-09-01', '2026-09-23']);
      assert.deepEqual(presetRange('LAST_MONTH', WEDNESDAY), ['2026-08-01', '2026-08-31']);
  });

  test('周日也落在它所在的那一周（周一为起点，不跳到下一周）', () => {
      // 2026-09-27 是星期日
      assert.deepEqual(presetRange('THIS_WEEK', '2026-09-27'), ['2026-09-21', '2026-09-27']);
      assert.deepEqual(presetRange('LAST_WEEK', '2026-09-27'), ['2026-09-14', '2026-09-20']);
  });

  test('「本周 / 本月」截至今天，不含未来日子', () => {
      // 未来日子进区间 = 趋势图上凭空多一段零值尾巴，会被读成业绩掉了
      const thisWeek = presetRange('THIS_WEEK', WEDNESDAY);
      assert.equal(thisWeek[1], WEDNESDAY);
      const thisMonth = presetRange('THIS_MONTH', WEDNESDAY);
      assert.equal(thisMonth[1], WEDNESDAY);
      // 「上周」是完整周：周一到周日
      assert.deepEqual(presetRange('LAST_WEEK', WEDNESDAY), ['2026-09-14', '2026-09-20']);
  });

  test('默认区间是本月，且与预设集合里的同一条完全一致', () => {
      assert.deepEqual(defaultDateRange(WEDNESDAY), presetRange('THIS_MONTH', WEDNESDAY));
  });

  test('预设标签齐全且顺序固定，日期选择器与文案不会两处各写一份', () => {
      const presets = datePresets(WEDNESDAY);
      assert.deepEqual(
          presets.map((item) => item.label),
          ['昨日', '本周', '上周', '本月', '上月']
      );
      for (const preset of presets) {
          assert.match(preset.value[0], /^\d{4}-\d{2}-\d{2}$/);
          assert.match(preset.value[1], /^\d{4}-\d{2}-\d{2}$/);
          assert.ok(preset.value[0] <= preset.value[1], `${preset.label} 起止倒挂`);
      }
  });

  // ------------------------------------------------------------------
  // 跨度上限
  // ------------------------------------------------------------------

  test('366 天整放行，367 天拦下并说清改哪里', () => {
      assert.equal(rangeOverLimitError(['2026-09-23', '2027-09-23']), '');
      const message = rangeOverLimitError(['2026-09-23', '2027-09-24']);
      assert.match(message, /366/);
      assert.match(message, /367/);
  });

  test('区间缺失时不在前端报错，交给后端的日期必填', () => {
      assert.equal(rangeOverLimitError(undefined), '');
      assert.equal(rangeOverLimitError(null), '');
      assert.equal(rangeOverLimitError(['2026-09-01', '']), '');
  });

  // ------------------------------------------------------------------
  // deep-link
  // ------------------------------------------------------------------

  test('deep-link 只接受两个完整的 yyyy-MM-dd，其余一律按未提供', () => {
      assert.deepEqual(rangeFromQuery({startDate: '2026-09-01', endDate: '2026-09-30'}), ['2026-09-01', '2026-09-30']);
      // 半截区间：目标页会因此回落到本月，而不是带着脏值去查
      assert.equal(rangeFromQuery({startDate: '2026-09-01'}), undefined);
      assert.equal(rangeFromQuery({endDate: '2026-09-30'}), undefined);
      assert.equal(rangeFromQuery({startDate: '2026-9-1', endDate: '2026-09-30'}), undefined);
      assert.equal(rangeFromQuery({startDate: '<script>', endDate: '2026-09-30'}), undefined);
      assert.equal(rangeFromQuery(undefined), undefined);
      // `?k=a&k=b` 取第一个值，与站内其它 deep-link 同一规则
      assert.deepEqual(rangeFromQuery({startDate: ['2026-09-01', '2026-09-02'], endDate: '2026-09-30'}), [
          '2026-09-01',
          '2026-09-30',
      ]);
  });

  test('某日区间两端同值；非法日回落默认区间而不是空参数', () => {
      assert.deepEqual(dayRange('2026-09-15'), ['2026-09-15', '2026-09-15']);
      assert.deepEqual(dayRange('bad-date'), [defaultDateRange()[0], defaultDateRange()[0]]);
  });

  // ------------------------------------------------------------------
  // 查询装配
  // ------------------------------------------------------------------

  test('空串与 null 的筛选项被省略，不把空字符串发给后端', () => {
      const query = buildReportQuery(
          ['2026-09-01', '2026-09-30'],
          {keyword: '', movementType: null, warehouseId: undefined, supplierId: 7},
          {pageNum: 2, pageSize: 50}
      );
      assert.deepEqual(query, {supplierId: 7, startDate: '2026-09-01', endDate: '2026-09-30', pageNum: 2, pageSize: 50});
      assert.ok(!('keyword' in query), '空关键字必须省略');
      assert.ok(!('movementType' in query), '空枚举必须省略');
  });

  test('导出请求不带分页；带 tab 时才带分页', () => {
      const exportOnly = buildReportQuery(['2026-09-01', '2026-09-30'], {keyword: 'x'});
      assert.ok(!('pageNum' in exportOnly) && !('pageSize' in exportOnly), '导出不该带分页参数');
      const paged = buildReportQuery(['2026-09-01', '2026-09-30'], {keyword: 'x'}, {pageNum: 1, pageSize: 20});
      assert.equal(paged.pageNum, 1);
  });

  test('日期区间不被共享筛选里的同名字段盖掉', () => {
      // 页面类型上 shared 不含日期，但这条不变量值得钉住：口径不能由一个下拉决定
      const query = buildReportQuery(['2026-09-01', '2026-09-30'], {startDate: '2020-01-01', endDate: '2020-01-02'});
      assert.deepEqual([query.startDate, query.endDate], ['2026-09-01', '2026-09-30']);
  });

  test('区间不完整时不发送半截日期', () => {
      const query = buildReportQuery(['2026-09-01', ''], {keyword: 'x'});
      assert.ok(!('startDate' in query) && !('endDate' in query));
  });

  // ------------------------------------------------------------------
  // Tab 隔离
  // ------------------------------------------------------------------

  test('每个 Tab 一份状态：归页码只影响目标 Tab', () => {
      const product = createTabView();
      const category = createTabView();
      product.pageNum = 3;
      category.pageNum = 5;

      enterTab(product);

      assert.equal(product.pageNum, 1, '目标 Tab 必须回到第 1 页');
      assert.equal(category.pageNum, 5, '其它 Tab 不能被连带重置（否则就是串状态）');
      assert.notEqual(product, category, '两个 Tab 必须是不同对象');
  });

  test('enterTab 不重置 pageSize：每页条数是用户偏好，不是查询条件', () => {
      const view = createTabView();
      view.pageSize = 100;
      view.pageNum = 4;
      enterTab(view);
      assert.equal(view.pageNum, 1);
      assert.equal(view.pageSize, 100);
  });

  test('Tab 状态自带空数组与零总数，模板不必判空', () => {
      const view = createTabView();
      assert.deepEqual(view.rows, []);
      assert.equal(view.total, 0);
      assert.equal(view.error, '');
      assert.equal(view.loading, false);
      assert.equal(view.pageNum, 1);
  });

  // ------------------------------------------------------------------
  // 0 与 null
  // ------------------------------------------------------------------

  test('定点数三态：null 是 —，"0.0000" 原样是 0.0000', () => {
      assert.equal(textOrDash(null), '—');
      assert.equal(textOrDash(undefined), '—');
      assert.equal(textOrDash(''), '—');
      assert.equal(textOrDash('0.0000'), '0.0000');
      assert.equal(textOrDash('1234.5600'), '1234.5600');
  });

  test('计数三态：0 笔要显示 0，没有事实才显示 —', () => {
      assert.equal(countText(0), '0');
      assert.equal(countText(null), '—');
      assert.equal(countText(undefined), '—');
      assert.equal(countText(128), '128');
  });

  test('图表取数保留 null，不把它塌成 0', () => {
      assert.equal(chartValue(null), null);
      assert.equal(chartValue(''), null);
      assert.equal(chartValue('abc'), null, '脏数据不该画成零值');
      assert.equal(chartValue('0.0000'), 0);
      assert.equal(chartValue('1234.5600'), 1234.56);
  });

  test('成本门禁：无权限时连 0 都不显示', () => {
      assert.equal(costText('88.0000', false), '—');
      assert.equal(costText('0.0000', false), '—');
      assert.equal(costText('88.0000', true), '88.0000');
      assert.equal(costText(null, true), '—');
  });

  test('无成本权限时裁掉成本列，有权限时原样返回同一数组', () => {
      const columns = [{dataIndex: 'warehouseName'}, {dataIndex: 'unitCost'}, {dataIndex: 'costAmount'}, {dataIndex: 'quantity'}];
      const hidden = filterCostColumns(columns, ['unitCost', 'costAmount'], false);
      assert.deepEqual(
          hidden.map((column) => column.dataIndex),
          ['warehouseName', 'quantity']
      );
      assert.equal(filterCostColumns(columns, ['unitCost'], true), columns);
  });

  test('布尔事实三态：未知不等于否', () => {
      assert.equal(yesNoText(true), '是');
      assert.equal(yesNoText(false), '否');
      assert.equal(yesNoText(null), '—');
      assert.equal(yesNoText(undefined), '—');
  });

  // ------------------------------------------------------------------
  // 不完整成本提示
  // ------------------------------------------------------------------

  test('跳过无成本行时必须显性提示，0 与缺失则不提示', () => {
      assert.equal(incompleteCostHint(3, '采购入库成本金额'), '采购入库成本金额已跳过 3 行无成本流水，不是全部采购入库成本金额');
      assert.equal(incompleteCostHint(0, '采购入库成本金额'), '');
      assert.equal(incompleteCostHint(null, '采购入库成本金额'), '');
  });

  // ------------------------------------------------------------------
  // 枚举与方向
  // ------------------------------------------------------------------

  test('枚举文案命不中时回落原值，不把未知类型藏成 —', () => {
      const labels = {PURCHASE_IN: {value: 'PURCHASE_IN', desc: '采购入库'}};
      assert.equal(enumDescText('PURCHASE_IN', labels), '采购入库');
      assert.equal(enumDescText('FUTURE_TYPE', labels), 'FUTURE_TYPE');
      assert.equal(enumDescText(null, labels), '—');
      assert.deepEqual(optionLabel([{value: 'STANDARD', label: '标品'}], 'STANDARD'), '标品');
      assert.equal(optionLabel([{value: 'STANDARD', label: '标品'}], 'OTHER'), 'OTHER');
  });

  test('方向只由传入的入方向集合派生，报表侧不再判字符串', () => {
      const inbound = ['PURCHASE_IN', 'STOCKTAKE_GAIN', 'GAIN_REPORT', 'TRANSFER_IN', 'CONVERT_IN'];
      assert.equal(movementDirection('PURCHASE_IN', inbound), 'IN');
      assert.equal(movementDirection('SALES_OUT', inbound), 'OUT');
      assert.equal(movementDirection('LOSS_REPORT', inbound), 'OUT');
      assert.equal(movementDirection('STOCKTAKE_GAIN', inbound), 'IN');
      assert.equal(movementDirection(null, inbound), null);
      assert.equal(directionText('IN'), '入');
      assert.equal(directionText('OUT'), '出');
      assert.equal(directionText(null), '—');
  });
}

// ---------------------------------------------------------------- 来源：report-receipt-columns-contract.test.mjs
{
  /**
   * 报表中心——收货与入库页的列精简与数值样式契约测试。
   *
   * `report-receipt-list.vue` 是报表中心里唯一没被列对齐改动覆盖的页面
   * （那次改了 overview / purchase / sales / inventory 与九个 tab 组件），
   * 所以它是唯一还在用自己那套「编码列 + 局部 `.num`」写法的页面。
   *
   * 本文件钉住两条容易被回退的纪律：
   *
   * 1. 商品身份不做四列：`商品名称` + `商品规格` 两列足够；把 `商品编码` / `商品规格编码`
   *    也各占一列会让一行商品身份吃满 4 列 600px。规则是「编码默认隐藏，除非是对账 / 导出核验
   *    场景」——本页虽是核验场景，但同一份报表里沿用已对齐页面（如 sales-product-tab）
   *    的两列写法才是一致的口径（导出走 Excel，不靠表格列）。
   * 2. 数值样式走公共类：`.scm-money` / `.scm-quantity` 是全局唯一的金额/数量样式，
   *    页面里再定义一个局部 `.num` 会造成「同一个数字在不同报表里字体不同」。
   *
   * `scroll.x` 必须由当前显示列的 width 合计，列设置变化后不能沿用旧总宽。
   */

  /** 读源码并剥掉注释（先剥 HTML 注释再剥块注释，顺序不能反）。 */
  function code(relative) {
      return readFileSync(new URL(relative, import.meta.url), 'utf8')
          .replace(/<!--[\s\S]*?-->/g, '')
          .replace(/\/\*[\s\S]*?\*\//g, '')
          .replace(/^\s*\/\/.*$/gm, '');
  }

  const receipt = code('../src/views/business/scm/report/report-receipt-list.vue');

  /** 取某个列数组声明的全部 width 之和；数组名不存在就断言失败。 */
  function widthSum(source, arrayName) {
      // 泛型参数里还有 `>`（`ref<TableColumnsType<ReceiptRow>>([])`），不能用 `[^>]*` 收尾
      const matched = new RegExp(`${arrayName}\\s*=\\s*ref[\\s\\S]*?\\(\\[([\\s\\S]*?)\\]\\);`).exec(source);
      assert.ok(matched, `找不到列数组 ${arrayName}`);
      const widths = [...matched[1].matchAll(/width:\s*(\d+)/g)].map((item) => Number(item[1]));
      assert.ok(widths.length > 0, `${arrayName} 里没有任何列宽`);
      return widths.reduce((total, item) => total + item, 0);
  }

  test('收货明细：商品身份只占两列，不复制编码列', () => {
      assert.match(receipt, /title: '商品', dataIndex: 'productName'/);
      assert.match(receipt, /title: '商品规格', dataIndex: 'skuName'/);
      // 编码默认隐藏。商品编码 / 商品规格编码 不得再各占一列
      assert.ok(!/title: '商品编码'/.test(receipt), '收货明细仍有独立的「商品编码」列');
      assert.ok(!/title: '商品规格编码'/.test(receipt), '收货明细仍有独立的「商品规格编码」列');
      assert.ok(!/dataIndex: 'spuCode'/.test(receipt), '收货明细仍在渲染 spuCode');
  });

  test('收款与入库明细：金额与数量列左对齐，且无局部 .num 样式', () => {
      // 2026-10-18 起全站只有枚举/分类/操作居中，数值列不再右对齐
      for (const index of [
          'receivedQuantity',
          'cumulativeReceivedQuantity',
          'remainingQuantity',
          'overReceiptQuantity',
          'receiptDifference',
          'purchasePrice',
          'receiptReferenceAmount',
          'quantity',
          'unitCost',
          'costAmount',
      ]) {
          assert.doesNotMatch(
              receipt,
              new RegExp(`dataIndex: '${index}'[\\s\\S]{0,60}?align:`),
              `${index} 列不应再声明 align`,
          );
      }
      // 数值渲染统一走全局类，页面里不得再定义局部 .num
      assert.ok(!/class="num"/.test(receipt), '仍在用局部 .num 类渲染数值');
      assert.ok(!/\.num\s*\{/.test(receipt), '仍定义了局部 .num 样式');
      assert.match(receipt, /class="scm-quantity"/, '数量列未使用 .scm-quantity');
      assert.match(receipt, /class="scm-money"/, '金额列未使用 .scm-money');
  });

  test('明细表按当前列宽计算 scroll.x', () => {
      for (const [emptyText, columnName] of [
          ['暂无收货明细', 'receiptColumns'],
          ['暂无入库明细', 'visibleInboundColumns'],
          ['暂无待入库收货单', 'pendingColumns'],
      ]) {
          const pattern = new RegExp(`emptyText: '${emptyText}'[\\s\\S]{0,80}?:scroll="\\{x: scmColumnsWidth\\(${columnName}\\)\\}"`);
          assert.match(receipt, pattern);
      }
      assert.ok(widthSum(receipt, 'receiptColumns') > 0);
      assert.ok(widthSum(receipt, 'inboundColumns') > 0);
  });

  test('待入库：操作列居中且只有一个回原单的跳转', () => {
      assert.match(receipt, /dataIndex: 'action'[\s\S]{0,120}?align: 'center'/);
      assert.match(receipt, /查看原收货单/);
      // 报表页不得有入库写入口（finance-report-contract 已锁，这里只锁它没有随列精简被重新引入）
      assert.ok(!/<a-button[^>]*>[^<]*(确认入库|办理入库)/.test(receipt));
  });
}

// ---------------------------------------------------------------- 来源：scm-advanced-filter-contract.test.mjs
{
  const read = file => readFileSync(new URL(file, import.meta.url), 'utf8');

  const product = read('../src/views/business/scm/product/product-list.vue');
  const sales = read('../src/views/business/scm/report/report-sales-list.vue');
  const purchase = read('../src/views/business/scm/report/report-purchase-list.vue');
  const productApi = read('../src/api/business/scm/product-api.ts');
  const reportApi = read('../src/api/business/scm/report-api.ts');
  const reportController = read('../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/report/controller/ScmReportController.java');
  const productService = read('../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/product/service/ProductQueryService.java');

  test('动态条件来自服务端权限约束的字段列表', () => {
    assert.match(productApi, /query\/filters/);
    assert.match(reportApi, /sales\/order\/query\/filters/);
    assert.match(reportApi, /purchase\/product\/query\/filters/);
    assert.match(reportController, /@SaCheckPermission\(ScmReportPermission\.SALES_QUERY\)/);
    assert.match(productService, /ProductAdvancedFilters\.apply\(form\)/);
  });

  test('报表试点保留组合筛选，商品档案只使用页面筛选项', () => {
    for (const page of [sales, purchase]) {
      assert.match(page, /ScmAdvancedFilterBuilder/);
      assert.match(page, /v-model="advancedFilters"/);
      assert.match(page, /advancedFilters:\s*(?:activeTab\.value\s*===\s*'(?:order|product)'\s*&&\s*)?advancedFilters\.value\.length/);
    }
    assert.doesNotMatch(product, /ScmAdvancedFilterBuilder|ScmSavedFilterPresets|advancedFilters/);
    assert.match(sales, /tab === order && advancedFilters\.value\.length/);
    assert.match(purchase, /tab === product && advancedFilters\.value\.length/);
  });
}

// ---------------------------------------------------------------- 来源：scm-filter-presets-contract.test.mjs
{
  const read = file => readFileSync(new URL(file, import.meta.url), 'utf8');
  const api = read('../src/api/business/scm/filter-preset-api.ts');
  const component = read('../src/components/business/scm/advanced-filter/saved-presets.vue');
  const service = read('../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/preference/filter/UserFilterPresetService.java');
  const dao = read('../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/preference/filter/UserFilterPresetDao.java');
  const product = read('../src/views/business/scm/product/product-list.vue');
  const sales = read('../src/views/business/scm/report/report-sales-list.vue');
  const purchase = read('../src/views/business/scm/report/report-purchase-list.vue');

  test('保存方案从登录上下文取得所有者，不允许客户端指定其它用户', () => {
    assert.match(service, /ScmOperator\.current\(\)/);
    assert.match(service, /StpUtil\.checkPermission/);
    assert.match(service, /ScmAdvancedFilterCatalog\.resolve/);
    assert.match(dao, /owner_key = #\{ownerKey\} AND view_key = #\{viewKey\}/);
    assert.doesNotMatch(api, /ownerKey|userId/);
  });

  test('报表页面保留方案操作，商品档案不再显示组合筛选方案', () => {
    assert.match(component, /scmFilterPresetApi\.create/);
    assert.match(component, /scmFilterPresetApi\.remove/);
    assert.match(component, /preset\.conditions\.map\(c => \(\{\.\.\.c\}\)\)/);
    assert.doesNotMatch(product, /ScmSavedFilterPresets|view-key="PRODUCT"|applySavedFilters/);
    assert.match(sales, /view-key="SALES_ORDER"/);
    assert.match(purchase, /view-key="PURCHASE_PRODUCT"/);
    for (const page of [sales, purchase]) {
      assert.match(page, /@apply="applySavedFilters"/);
      assert.match(page, /advancedFilters\.value = conditions/);
    }
  });
}
