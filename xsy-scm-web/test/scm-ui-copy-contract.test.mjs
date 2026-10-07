/**
 * SCM UI 文案与信息层级的契约单测。
 *
 * ## 为什么需要这份契约
 *
 * 项目曾系统性地把开发设计说明、业务规则推导、技术实现细节直接写进业务 UI：
 * 常驻蓝色说明框、表单 `help` 长句、报表工具栏口径注释，甚至把 `path` / `parent_id` /
 * `SALES_OUT` / `occurred_at` 这类字段名与枚举名展示给业务用户。
 *
 * 典型反例（计量单位编辑弹窗）：
 *
 * ```text
 * 单位名称
 * 业务表按名称记账，改名会让历史数据失去真值来源     ← 数据模型解释
 * 建议小数位
 * 只约束前端输入，不改变数据库精度                   ← 纯实现细节
 * ```
 *
 * 治理规则见 `docs/architecture/scm-ui-guidelines.md §8`：
 * 不要为了证明系统设计严谨，而让 UI 替代码写设计文档。
 * 代码注释、ADR、测试负责解释「为什么」；业务 UI 负责告诉用户「现在能做什么」。
 *
 * 本文件钉住治理结果，防止「随手再补一句说明」把噪音重新引回来。
 *
 * ## 为什么用 AST 而不是正则
 *
 * 曾经的一次性扫描用「正则 + 人工排除」，因为要区分「渲染文案」和「源码注释」，
 * 还要排除组件标签名、SVG 属性、`v-for :key`。人工复核可以容忍误报，长期跑的测试不允许。
 *
 * 因此这里用 `vue-eslint-parser`（项目 `devDependencies` 直接依赖）解析 SFC 的 template AST，
 * 只看两类目标：
 *
 * - 文本节点（`VText`）；
 * - `placeholder` / `help` / `title` / `message` / `description` / `label` / `extra` / `empty-text`
 *   的属性值，绑定形式（`:message="…"`）先取表达式里的字面文本再检查。
 *
 * AST 天然放过、不需要额外排除逻辑的：
 *
 * | 形态 | 处理 |
 * | --- | --- |
 * | `@click="save"`、`v-if="…"`、`:key="row.fileKey"` | 不查（指令表达式） |
 * | `import FinanceDetailDrawer`、`<FinanceDetailDrawer>` | 不查（组件标签名 / `<script>`） |
 * | `<!-- 后端说明 -->` | 不查（注释节点） |
 * | `<script>` 里的 `const fileKey = …` | 不查（只解析 template） |
 * | `<svg><path d="…"/></svg>` | 不查（`svg` 子树整体跳过） |
 * | `{{ row.path }}` | 不查（`VExpressionContainer` 无文本节点） |
 *
 * 这就是 `source.includes('后端')` 之类整串匹配不能用的原因：上面每一类都会误报。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, readdirSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {createRequire} from 'node:module';

const require = createRequire(import.meta.url);
const {parseForESLint} = require('vue-eslint-parser');
const typescriptParser = require('@typescript-eslint/parser');

const SRC = fileURLToPath(new URL('../src/', import.meta.url));
const SCM_VIEWS = `${SRC}views/business/scm/`;
const SCM_COMPONENTS = `${SRC}components/business/scm/`;

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

const norm = (path) => path.replace(/\\/g, '/');
const rel = (path) => norm(path).slice(norm(SRC).length);

const SCM_VIEW_FILES = collect(SCM_VIEWS, /\.vue$/);
const SCM_COMPONENT_FILES = collect(SCM_COMPONENTS, /\.vue$/);
/** 黑名单与用词规则扫描全量 SCM 界面；棘轮只针对业务页面。 */
const SCM_UI_FILES = [...SCM_VIEW_FILES, ...SCM_COMPONENT_FILES];

/** 用户可见的属性值。只有这些属性名会被读取，`d` / `key` / `type` 天然不在内。 */
const TEXT_ATTRIBUTES = new Set([
  'placeholder',
  'help',
  'title',
  'message',
  'description',
  'label',
  'extra',
  'empty-text',
]);

/** 子树整体跳过：不是用户可见文案，且最容易混入技术标识。 */
const SKIP_SUBTREE = new Set(['svg', 'script', 'style']);

/**
 * 标签名归一化：parser 会把标签名转小写，但 `ReportNote` 得到 `reportnote`、
 * `report-note` 得到 `report-note` —— 只有去掉连字符两者才等价。
 */
const tagOf = (node) => node.name.toLowerCase().replace(/-/g, '');

// ------------------------------------------------------------------
// 黑名单（docs/architecture/scm-ui-guidelines.md §8.2）
// ------------------------------------------------------------------

/**
 * `label` 用于断言失败时告诉维护者「违反了哪一类」。
 * 缩写与字段名加 `\b` 词边界：`商品SKU` 要命中（中文对 `\b` 而言是非单词字符），
 * `SKU_CODE` 里的 `SKU` 不能命中（后面紧跟 `_`，不构成词边界）。
 */
const BLACKLIST = [
  {label: '模块名 Finance', pattern: /Finance/},
  {label: '阶段代号 R0/R1/R2', pattern: /\bR[012]\b/},
  // 开发波次代号会以错误文案的形式漏到用户面前（实测 `W5 不做自动换算` 出现在 40971
  // 的用户可读文案里），因此必须与 R0/R1/R2 同等对待。
  {label: '阶段代号 Wn', pattern: /\bW\d{1,2}\b/},
  {label: '计划章节号 §', pattern: /§/},
  {label: '术语 SPU', pattern: /\bSPU\b/i},
  {label: '术语 SKU', pattern: /\bSKU\b/i},
  // 中文没有词边界：`商品规格` 里天然含有 `品规` 子串，必须排除，否则整表误报。
  {label: '术语 品规', pattern: /(?<!商)品规(?!格)/},
  {label: '术语 货品', pattern: /货品/},
  {label: '术语 单品', pattern: /单品/},
  {
    label: '字段名',
    pattern: /\b(path|parent_id|seller_id|fileKey|occurred_at|createTime)\b/,
  },
  {
    label: '枚举名',
    pattern: /\b(PURCHASE_IN|SALES_OUT|DIRECT|WAREHOUSE_CONFIRM)\b/,
  },
  {label: '错误码', pattern: /\b(40989|40995)\b/},
  {label: '技术词', pattern: /数据库|事务|幂等|真值|精度|后端|前端|服务端|客户端/},
  {label: '技术缩写 VO', pattern: /\bVO\b/},
];

// ------------------------------------------------------------------
// 解析与取值
// ------------------------------------------------------------------

/** 解析 SFC，只取 template 的 AST。`<script lang="ts">` 交给 TS parser，避免 espree 语法错误。 */
function templateRootOf(file) {
  const {ast} = parseForESLint(readFileSync(file, 'utf8'), {
    sourceType: 'module',
    ecmaVersion: 2022,
    parser: typescriptParser,
    parserOptions: {ecmaVersion: 2022, sourceType: 'module'},
  });
  return ast.templateBody;
}

/** 遍历 template AST，跳过 `svg` / `script` / `style` 子树。 */
function walk(root, visit) {
  if (!root) return;
  const stack = [root];
  while (stack.length) {
    const node = stack.pop();
    if (!node || typeof node.type !== 'string') continue;
    if (node.type === 'VElement' && SKIP_SUBTREE.has(tagOf(node))) continue;
    visit(node);
    if (Array.isArray(node.children)) {
      for (let index = node.children.length - 1; index >= 0; index -= 1) {
        stack.push(node.children[index]);
      }
    }
  }
}

/** 属性名：普通属性取 `key.name`，`v-bind:` / `:` 取指令参数名；其余（`v-bind="obj"`、`v-on`）返回 null。 */
function attributeName(attribute) {
  if (!attribute.directive) return attribute.key?.name ?? null;
  const argument = attribute.key?.argument;
  return argument?.type === 'VIdentifier' ? argument.name : null;
}

/**
 * 该元素是否带了某个指令。注意 `vue-eslint-parser` 把 `v-if` 的指令名解析成 `if`
 * （不带 `v-` 前缀，`rawName` 也不在 `key` 上），因此这里传的是 `'if'` 而不是 `'v-if'`。
 */
function hasDirective(element, name) {
  return (element.startTag?.attributes ?? []).some(
      (attribute) => attribute.directive && attribute.key?.name?.name === name);
}

/** 从绑定表达式中抽取字面文本；纯动态表达式（`:title="row.name"`）返回空数组。 */
function literalTexts(node, out = []) {
  if (!node) return out;
  switch (node.type) {
    case 'Literal':
      if (typeof node.value === 'string') out.push(node.value);
      break;
    case 'TemplateLiteral':
      for (const quasi of node.quasis) out.push(quasi.value.cooked ?? quasi.value.raw);
      for (const expression of node.expressions) literalTexts(expression, out);
      break;
    case 'BinaryExpression':
      if (node.operator === '+') {
        literalTexts(node.left, out);
        literalTexts(node.right, out);
      }
      break;
    case 'LogicalExpression':
    case 'ConditionalExpression':
      literalTexts(node.left ?? node.test, out);
      literalTexts(node.right ?? node.consequent, out);
      if (node.alternate) literalTexts(node.alternate, out);
      break;
    case 'ArrayExpression':
      for (const element of node.elements) literalTexts(element, out);
      break;
    default:
      break;
  }
  return out;
}

/**
 * 收集一个 SFC 里全部用户可见文案。
 * 返回 `{text, where}`，`where` 形如 `placeholder` 或 `text`，用于定位。
 */
function visibleCopyOf(file) {
  const found = [];
  walk(templateRootOf(file), (node) => {
    if (node.type === 'VText') {
      if (/\S/.test(node.value)) found.push({text: node.value, where: 'text'});
      return;
    }
    if (node.type !== 'VElement') return;
    for (const attribute of node.startTag?.attributes ?? []) {
      const name = attributeName(attribute);
      if (!name || !TEXT_ATTRIBUTES.has(name)) continue;
      if (!attribute.directive) {
        if (typeof attribute.value?.value === 'string') {
          found.push({text: attribute.value.value, where: name});
        }
        continue;
      }
      for (const text of literalTexts(attribute.value?.expression)) {
        if (/\S/.test(text)) found.push({text, where: name});
      }
    }
  });
  return found;
}

/** 扫描全部 SCM 界面，按「违反类别 → 命中」聚合。 */
function blacklistHits() {
  const hits = [];
  for (const file of SCM_UI_FILES) {
    for (const {text, where} of visibleCopyOf(file)) {
      for (const {label, pattern} of BLACKLIST) {
        if (pattern.test(text)) hits.push({file: rel(file), where, label, text: text.trim()});
      }
    }
  }
  return hits;
}

/** 统计某个形态的出现次数，可限定文件范围。 */
function countShape(files, predicate) {
  let total = 0;
  const offenders = [];
  for (const file of files) {
    let perFile = 0;
    walk(templateRootOf(file), (node) => {
      if (predicate(node)) perFile += 1;
    });
    if (perFile > 0) {
      total += perFile;
      offenders.push({file: rel(file), count: perFile});
    }
  }
  return {total, offenders};
}

const isElement = (name) => {
  const expected = name.toLowerCase().replace(/-/g, '');
  return (node) => node.type === 'VElement' && tagOf(node) === expected;
};

/** 常驻 `a-alert type="info"`：无 `v-if` / `v-show`，即「每次打开都占首屏」。 */
const isResidentInfoAlert = (node) => {
  if (!isElement('a-alert')(node)) return false;
  if (hasDirective(node, 'if') || hasDirective(node, 'show')) return false;
  const typeAttribute = (node.startTag?.attributes ?? []).find(
      (attribute) => !attribute.directive && attribute.key?.name === 'type');
  return typeAttribute?.value?.value === 'info';
};

/** 表单 `help`：不区分静态/绑定，属性名命中即计入。 */
const hasHelpAttribute = (node) =>
    node.type === 'VElement' &&
    (node.startTag?.attributes ?? []).some(
        (attribute) => attributeName(attribute) === 'help');

/** `type="secondary"` 排版：单元格占位、单位、动态计数与解释性文案共用，只看总量棘轮。 */
const hasSecondaryType = (node) =>
    node.type === 'VElement' &&
    (node.startTag?.attributes ?? []).some((attribute) => {
      if (attributeName(attribute) !== 'type') return false;
      if (!attribute.directive) return attribute.value?.value === 'secondary';
      return literalTexts(attribute.value?.expression).includes('secondary');
    });

/**
 * 解释性提示的另一种形态：`class` 里带 `hint`（`coupon-hint` / `__hint` / `--hint`）。
 * 治理前统计「解释性 secondary 89 处」时用的就是这一类选择器，单看 `type="secondary"`
 * 会漏掉一大半，因此两种形态各钉一条棘轮。
 */
const hasHintClass = (node) =>
    node.type === 'VElement' &&
    (node.startTag?.attributes ?? []).some((attribute) => {
      if (attributeName(attribute) !== 'class') return false;
      const values = attribute.directive
          ? literalTexts(attribute.value?.expression)
          : typeof attribute.value?.value === 'string' ? [attribute.value.value] : [];
      return values.some((value) => /hint/i.test(value));
    });

// ------------------------------------------------------------------
// 基线（治理完成后复扫所得，棘轮只降不升）
// ------------------------------------------------------------------

/**
 * 基线 = 文案收口完成后由本文件的扫描器跑出来的真实值，棘轮只降不升。
 *
 * `type="secondary"` 与 `hint` 类分开计数：前者是 antd 排版的属性形态，后者是页面自建的
 * 提示样式（`coupon-hint` / `__hint`），两者不重叠也不互相替代。每个数字只用于
 * 「不比上一次多」，不要跨口径对数 —— 尤其不要把纯渲染的 `secondary`
 * （单元格占位 `—`、单位、`已选 N 个`）当成待删项：删了会破坏表格显示。
 *
 * 提高基线必须在 PR 里说明「不告知会导致什么操作错误」，否则应改为
 * Tooltip / 口径说明 / 操作时确认（见 scm-ui-guidelines.md §8.1）。
 */
const BASELINE = {
  residentInfoAlert: 19,
  formHelp: 2,
  // 32 = 收口后的 31 + `purchase-demand-summary-preview.vue` 那句「不是最终净采购建议」
  // （会改变用户决策的免责声明，按 guidelines §8.1 保留，不是回归）。
  secondaryType: 32,
  hintClass: 54,
  reportResidentInfoAlert: 0,
  // 24 → 23：采购概览那两条讲的是导出范围与无授权仓库时的表现（机制），不是数字口径，已删。
  reportNoteUsers: 23,
};

const REPORT_FILES = SCM_VIEW_FILES.filter((file) => rel(file).includes('/report/'));

// ------------------------------------------------------------------
// 1. 扫描基线有效
// ------------------------------------------------------------------

test('扫描基线有效：SCM 界面文件都能被解析出 template', () => {
  assert.ok(SCM_VIEW_FILES.length > 140, `只枚举到 ${SCM_VIEW_FILES.length} 个 SCM 页面，扫描方式可能已失效`);
  assert.ok(REPORT_FILES.length > 20, `只枚举到 ${REPORT_FILES.length} 个报表文件`);
  for (const file of SCM_UI_FILES) {
    assert.ok(templateRootOf(file), `${rel(file)} 没有解析出 template，AST 扫描已失效`);
  }
});

// ------------------------------------------------------------------
// 2. 黑名单：术语、字段名、枚举名、技术词
// ------------------------------------------------------------------

test('UI 可见文案不含黑名单术语与实现细节', () => {
  const hits = blacklistHits();
  assert.deepEqual(
      hits.map((hit) => `${hit.file} [${hit.where}] ${hit.label} → ${hit.text}`),
      [],
      `UI 文案命中了 ${hits.length} 处黑名单词；业务界面只说「能做什么」，不解释「怎么实现」`,
  );
});

// ------------------------------------------------------------------
// 3. UI 用词规则（AGENTS.md）
// ------------------------------------------------------------------

test('UI 可见文案不出现 SPU / SKU / 品规 / 货品 / 单品', () => {
  const pattern = /\bSPU\b|\bSKU\b|(?<!商)品规(?!格)|货品|单品/i;
  const hits = [];
  for (const file of SCM_UI_FILES) {
    for (const {text, where} of visibleCopyOf(file)) {
      if (pattern.test(text)) hits.push(`${rel(file)} [${where}] → ${text.trim()}`);
    }
  }
  assert.deepEqual(hits, [], '用户界面统一使用 商品 / 商品规格，不出现 SPU / SKU / 品规 / 货品 / 单品');
});

// ------------------------------------------------------------------
// 4. 常驻解释性文案：棘轮只降不升
// ------------------------------------------------------------------

test('常驻 info alert 数量不超过基线（新增需在 PR 说明理由）', () => {
  const {total, offenders} = countShape(SCM_VIEW_FILES, isResidentInfoAlert);
  assert.ok(
      total <= BASELINE.residentInfoAlert,
      `常驻 info alert 从 ${BASELINE.residentInfoAlert} 涨到 ${total}；` +
      '「不告诉用户就可能操作错误」的信息才留在界面上，其余收进 Tooltip / 口径说明 / 操作时确认。' +
      `当前分布：${JSON.stringify(offenders)}`,
  );
});

test('表单 help 数量不超过基线（示例值改用 placeholder）', () => {
  const {total, offenders} = countShape(SCM_VIEW_FILES, hasHelpAttribute);
  assert.ok(
      total <= BASELINE.formHelp,
      `表单 help 从 ${BASELINE.formHelp} 涨到 ${total}；示例值走 placeholder，长解释走帮助文档。` +
      `当前分布：${JSON.stringify(offenders)}`,
  );
});

test('secondary 排版数量不超过基线（解释性文案收口后不应反弹）', () => {
  const {total} = countShape(SCM_VIEW_FILES, hasSecondaryType);
  assert.ok(
      total <= BASELINE.secondaryType,
      `secondary 排版从 ${BASELINE.secondaryType} 涨到 ${total}；` +
      '纯渲染占位（`—`、单位、`已选 N 个`）保留，新增解释性说明请先确认必要性。',
  );
});

test('hint 类提示数量不超过基线', () => {
  const {total, offenders} = countShape(SCM_VIEW_FILES, hasHintClass);
  assert.ok(
      total <= BASELINE.hintClass,
      `hint 类提示从 ${BASELINE.hintClass} 涨到 ${total}：${JSON.stringify(offenders)}；` +
      '这类文案和 `type="secondary"` 一样属于「常驻解释」，新增前先确认用户不被告知就会操作错误。',
  );
});

// ------------------------------------------------------------------
// 5. 报表口径说明：统一走 ReportNote，不再常驻 Alert
// ------------------------------------------------------------------

test('report 下不再有常驻 info alert', () => {
  const {total, offenders} = countShape(REPORT_FILES, isResidentInfoAlert);
  assert.ok(
      total <= BASELINE.reportResidentInfoAlert,
      `report 下仍有 ${total} 处常驻 info alert：${JSON.stringify(offenders)}；` +
      '统计口径统一收进 report-note 的 Popover / Drawer。',
  );
});

test('report-note 组件存在且自带硬规则护栏', () => {
  const component = `${SCM_COMPONENTS}report-note/index.vue`;
  const source = readFileSync(component, 'utf8');
  assert.match(source, /props\.points\.length > 3/, 'report-note 未约束 Popover 条数（最多 3 条）');
  assert.match(source, /point\.length > 40/, 'report-note 未约束单条长度（每条 ≤ 40 字）');
  assert.match(source, /sections/, 'report-note 未提供分组（Drawer）形态，长口径只能塞进 Popover');

  const users = countShape(SCM_VIEW_FILES, (node) => isElement('report-note')(node)).total;
  assert.ok(
      users >= BASELINE.reportNoteUsers,
      `report-note 使用点从 ${BASELINE.reportNoteUsers} 降到 ${users}；口径说明不应退回常驻正文。`,
  );
});
