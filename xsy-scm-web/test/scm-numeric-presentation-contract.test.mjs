/**
 * SCM 数值与编号视觉口径的契约单测（Sprint F-B）。
 *
 * ## 为什么需要这份契约
 *
 * 项目里曾有 **19 个页面各自定义** `.num { font-family: ui-monospace, … }`，
 * 共 **55 处使用点**。这些局部类把三种不同语义压成同一种视觉：
 *
 * - 金额（要纵向比大小）
 * - 数量（要纵向比大小）
 * - 单号 / 编码（要逐位抄录比对）
 *
 * 结果是口径无法统一演进：改一处不影响其余 18 处，且 `theme/scm` 里根本没有对应的公共类。
 *
 * Sprint F-B 把这 55 处按字段语义迁到公共类，并删掉全部局部定义。本文件钉住这个结果，
 * 防止「随手再写一个 .num」把口径重新打散。
 *
 * ## 口径（docs/plan/active/frontend-ui-closeout-decisions.md §C1）
 *
 * - `.scm-money`    金额 / 单价 / 成本 —— 比例字体 + tabular-nums
 * - `.scm-quantity` 数量 / 百分比 / 统计数字 —— 比例字体 + tabular-nums
 * - `.scm-mono`     单号 / 编码 / 外部流水号 —— **等宽字体** + tabular-nums
 *
 * 注意 `.scm-mono` 本轮只建立公共能力，标识字段的全项目接入是独立审计，不在本契约断言范围内。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, readdirSync} from 'node:fs';
import {fileURLToPath} from 'node:url';

const SRC = fileURLToPath(new URL('../src/', import.meta.url));
const SCM_VIEWS = `${SRC}views/business/scm/`;
const TABLE_LESS = '../src/theme/scm/table.less';
const THEME_INDEX = '../src/theme/scm/index.less';

/** 读源码并剥掉注释 —— 注释里提到反例名字是合规的，不能算违规。 */
function stripComments(source) {
  return source
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
}

/** 递归收集目录下的文件，可按扩展名过滤。 */
function collect(dir, pattern) {
  const out = [];
  for (const entry of readdirSync(dir, {withFileTypes: true})) {
    const path = `${dir}${entry.name}`;
    if (entry.isDirectory()) out.push(...collect(`${path}/`, pattern));
    else if (pattern.test(entry.name)) out.push(path);
  }
  return out;
}

const SCM_VUE = collect(SCM_VIEWS, /\.vue$/);
const ALL_SRC_VUE = collect(SRC, /\.vue$/);

/** 局部 `.num` 样式块定义：行首的 `.num {`。 */
const LOCAL_NUM_DEFINITION = /^\s*\.num\s*\{/m;
/** 局部 `.num` 使用：模板里的 class 绑定（含 `class="num"` / `:class="'num'"`）。 */
const LOCAL_NUM_USAGE = /["'`]num["'`]/;

test('扫描基线有效：SCM 视图与主题文件都能被枚举到', () => {
  assert.ok(SCM_VUE.length > 100, `只枚举到 ${SCM_VUE.length} 个 SCM 视图，扫描方式可能已失效`);
  assert.ok(ALL_SRC_VUE.length > SCM_VUE.length, '全量视图数应大于 SCM 视图数');
});

// ------------------------------------------------------------------
// 1 / 4. 不得再定义局部 .num
// ------------------------------------------------------------------

test('views/business/scm 下不再定义 .num', () => {
  const offenders = SCM_VUE.filter((file) =>
      LOCAL_NUM_DEFINITION.test(stripComments(readFileSync(file, 'utf8'))));
  assert.deepEqual(
      offenders.map((file) => file.slice(SCM_VIEWS.length)),
      [],
      'SCM 页面不得再自定义 .num；请改用 .scm-money / .scm-quantity / .scm-mono',
  );
});

test('全仓库业务页面不得重新声明 .num（防止口径再次打散）', () => {
  const offenders = ALL_SRC_VUE.filter((file) =>
      LOCAL_NUM_DEFINITION.test(stripComments(readFileSync(file, 'utf8'))));
  assert.deepEqual(
      offenders.map((file) => file.slice(SRC.length)),
      [],
      '任何页面都不得重新声明 .num；三种数值语义必须走 theme/scm 的公共类',
  );
});

// ------------------------------------------------------------------
// 2. 不得再使用局部 .num
// ------------------------------------------------------------------

test('views/business/scm 下不再使用 class="num"', () => {
  const offenders = [];
  for (const file of SCM_VUE) {
    if (LOCAL_NUM_USAGE.test(stripComments(readFileSync(file, 'utf8')))) {
      offenders.push(file.slice(SCM_VIEWS.length));
    }
  }
  assert.deepEqual(offenders, [], '以下文件仍在使用局部 .num class');
});

// ------------------------------------------------------------------
// 3. 公共数值样式必须存在且被引入
// ------------------------------------------------------------------

test('scm-money / scm-quantity / scm-mono 三个公共样式都存在', () => {
  const table = readFileSync(new URL(TABLE_LESS, import.meta.url), 'utf8');

  // 规则必须真的被引进来 —— 只建文件不 import 是最容易漏的一步
  const index = readFileSync(new URL(THEME_INDEX, import.meta.url), 'utf8');
  assert.match(index, /@import '\.\/table\.less';/, 'theme/scm/index.less 未引入 table.less');

  // 金额与数量：比例字体 + tabular-nums（共用一条规则，选择器成组出现）
  assert.match(table, /\.scm-money,\s*\n\.scm-quantity\s*\{[\s\S]{0,120}font-variant-numeric: tabular-nums/,
      '.scm-money / .scm-quantity 未共用 tabular-nums 规则');

  // 等宽类：必须是**等宽字体**，而不是只把数字拉齐 —— 这是它区别于 scm-quantity 的全部意义
  assert.match(table, /\.scm-mono\s*\{[\s\S]{0,240}font-family:[\s\S]{0,160}ui-monospace/,
      '.scm-mono 缺少 ui-monospace 等宽字族');
  assert.match(table, /\.scm-mono\s*\{[\s\S]{0,320}font-variant-numeric: tabular-nums/,
      '.scm-mono 缺少 tabular-nums');
});

test('迁移结果仍然存在：SCM 视图里确实在用公共数值类', () => {
  // 反向护栏：如果上面的「不得使用 .num」是因为扫描坏了才通过，这里会把它抓出来
  let money = 0;
  let quantity = 0;
  for (const file of SCM_VUE) {
    const body = readFileSync(file, 'utf8');
    money += [...body.matchAll(/class="scm-money"/g)].length;
    quantity += [...body.matchAll(/class="scm-quantity"/g)].length;
  }
  assert.ok(money >= 12, `只找到 ${money} 处 .scm-money，少于 F-B 迁移的 12 处`);
  assert.ok(quantity >= 43, `只找到 ${quantity} 处 .scm-quantity，少于 F-B 迁移的 43 处`);
});

/*
 * 刻意**不**断言「src 下只有 .scm-mono 用等宽字体」。
 *
 * 除 `.num` 外，项目里还有若干与本次口径无关的等宽样式：
 * `.scm-diff-key` / `.scm-diff-row-title`（变更对比的字段名）、`.log-time`（日志时间）、
 * `.report-kpi-value`（KPI 大字）。它们不是 `.num`，语义也未纳入 C1 的三类，
 * 属后续「标识类字段全项目接入审计」的输入，不在本次数值口径统一的断言范围内。
 */
