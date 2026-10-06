/**
 * SCM 生产源码注释噪声契约。
 *
 * 只约束<b>开发过程标记</b>（来源 / 复制日期 / 波次 / 阶段号 / 批次号 / 计划章节号 / 「新增文件」），
 * 不限制注释长度 —— 长度是审计信号，`longCommentCount` 只输出报告。
 *
 * <b>标记必须为 0</b>：任何命中都失败，确需保留的例外逐条登记在 `WHITELIST` 并写明理由。
 *
 * 标记用组合 / 条件匹配，避免误报：裸 `来源：` 会命中业务含义（`来源：销售订单`），
 * 裸 `§` 会命中指向 `docs/architecture` 的有效链接，裸 `P\d+` 会命中指向
 * `docs/decisions.md` 的「P0/P1/P2 裁决」与 `CONTRIBUTING.md` 的「P12 锁序」。
 *
 * 详细规则见 `docs/architecture/code-comment-guidelines.md`。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, readdirSync} from 'node:fs';
import {fileURLToPath} from 'node:url';

const REPO = fileURLToPath(new URL('../../', import.meta.url)).replace(/\\/g, '/');

/** 扫描范围。`db/migration` 单列出来排除 —— Flyway 已应用的 migration 不得改字节。 */
const SCOPES = [
  {
    key: 'xsy-scm-web/src',
    root: `${REPO}xsy-scm-web/src`,
    extensions: new Set(['.ts', '.vue', '.less', '.js', '.mjs', '.tsx']),
  },
  {
    // 测试源码只扫<b>注释</b>（`commentsOf` 对 `.mjs` 走 `codeComments`，天然跳过字符串、
    // 模板串与正则字面量），因此测试名、断言、正则与内联快照都不在射程内。
    // 但<b>契约测试自身</b>必须排除：它把标记模式写成注释与正则字面量，扫自己必然自证违规。
    key: 'xsy-scm-web/test',
    root: `${REPO}xsy-scm-web/test`,
    extensions: new Set(['.mjs']),
    excludedFiles: new Set([
      'xsy-scm-web/test/scm-comment-noise-contract.test.mjs',
      'xsy-scm-web/test/scm-ui-copy-contract.test.mjs',
    ]),
  },
  {
    key: 'xsy-scm-server/sa-admin/src/main/java/com/xsy/scm',
    root: `${REPO}xsy-scm-server/sa-admin/src/main/java/com/xsy/scm`,
    extensions: new Set(['.java']),
  },
  {
    key: 'xsy-scm-server/sa-admin/src/main/resources',
    root: `${REPO}xsy-scm-server/sa-admin/src/main/resources`,
    extensions: new Set(['.xml', '.yml', '.yaml', '.properties']),
  },
];

const EXCLUDED_DIRECTORIES = new Set([
  'node_modules', 'dist', 'dist-verify', 'target', '.runtime', 'archive',
  '__pycache__', '.vite', 'coverage',
]);

/**
 * Flyway 迁移目录：只从扫描中排除，永远不修改。
 *
 * 已应用的 migration 被 Flyway 记录 checksum（`validate-on-migrate: true`），改任何一个
 * 字节都会让后端启动时校验失败。因此这里连<b>注释</b>也不动 —— 哪怕只是把 `**x**` 换成
 * `<b>x</b>`。`resources` 范围的 extensions 不含 `.sql`，正是同一道防线。
 * 目录名本身不带尾斜杠，故不加尾斜杠比对。
 */
const FLYWAY_DIRECTORY = '/db/migration';

// ------------------------------------------------------------------
// 标记集（组合 / 条件匹配，见文件头）
// ------------------------------------------------------------------

const PROCESS_MARKERS = [
  ['来源：project-reference-examples', /来源\s*[:：]\s*\**\s*project-reference-examples/],
  ['来源：新写', /来源\s*[:：]\s*\**\s*新写/],
  // `来源：<b>W1 派生</b>` 是第三种形态，P4.1 的组合匹配没覆盖到
  ['来源：Wn 派生', /来源\s*[:：]\s*\**\s*派生/],
  ['复制日期', /复制日期/],
  ['Copy First + Adapt', /Copy\s*First/],
  ['剪枝 / 适配 / 验收记录', /(剪枝|适配|验收)\s*[:：]/],
  // 不能只匹配 `Playwright`：`表格 DOM id —— 给 Playwright 定位用` 是在解释
  // 「为什么有两套 id」，属正常约束（实测 4 处误报）。要求 `验收/测试` 在前。
  ['测试 / 验收记录', /(验收|测试)[^\n]{0,20}(Playwright|E2E|单测)|单测已过|测试已过|已通过测试/],
  ['参考项目 / 旧项目对比', /project-reference-examples|参考项目|参考实现|legacy 项目|V2 W\d/],
  ['为什么新增此文件', /为什么(必须)?(新增|新建)此?文件/],
  ['AI 指令式措辞', /绝不能改|不要乱动|刻意这样|不得改动|禁止修改|必须照做/],
  // closeout 补：这一批是「没有被 marker 集合覆盖」的历史计划标签，
  // 语境上仍是开发批次编号而不是当前业务概念。
  ['新增文件', /新增文件/],
  ['仿 xxx 文件', /仿\s*[`\w]/],
  ['Provenance 要求', /Provenance/],
  ['本阶段', /本阶段/],
  ['HD-Bn 批次号', /HD-[A-Z0-9-]+/],
  ['A.n 条目号', /\bA\.\d+\b/],
  // 裸 `P\d+` 会命中指向 `docs/decisions.md`「P0/P1/P2/P3 裁决」与
  // `CONTRIBUTING.md`「P12 锁序」的<b>活锚点</b>，因此只在没有这些后缀时才算标记。
  ['Pn 批次号（非裁决锚点）', /(?<!锁序 )\bP\d{1,2}\b(?!\s*(?:裁决|基线收口裁决|锁序))/],
  // 裸 `B\d+` 会命中缺口文档的 `Bn` 溯源（那是活契约），由 WHITELIST 逐文件放行。
  ['Bn 批次号（非缺口溯源）', /\bB\d{1,2}\b/],
  // 裸 `A\d+` 曾因「`A4` 是纸张尺寸」被放过，结果 `A17/A18/A25/A26/A31` 全漏了。
  // 现在改成<b>默认禁止 + 精确白名单放行</b>（纸张尺寸的两个文件已登记）。
  ['An 条目号（非业务值）', /\bA\d{1,2}\b/],
  // 「设计考古」：旧项目当时怎么做、旧口径的段落名。
  ['考古：C 的 / A 源 / C 段', /C 的|A 源|C 段|V2 原生/],
  // 开发波次。注意 `预配送波次`（`deliveryWave`）是真实业务字段，不能裸匹配 `波次`。
  ['开发波次', /(出库|盘点|报损报溢|调拨|阈值预警|规格转换)波次|波次新增|本波次/],
  // commit 考古：`历史：提交 48134bf 曾…`。Git 已是历史，源码不需要复述它。
  ['提交 sha 考古', /(历史|背景|提交)\s*[:：]?\s*(提交\s*)?[0-9a-f]{7,40}\b/],
  // 迁移版本号的<b>开发语境</b>：`V15 一并落库` / `V15 阶段` / `V15 旧版` / `V15 migration 提供`。
  // 裸 `V\d+` 不禁 —— `V34 移动加权`、`V63 ck_*`、`V61 web_perms` 是指向真实 migration 文件的
  // 有效溯源（文件存在且不可变），属当前事实。只有把它当"第几批开发"来讲时才是过程标记。
  // 后置语境词只收<b>动作/产物</b>（一并落库 / 播种 / 新建 / 引入）；裸「迁移」不收，
  // 好让「见 V34 迁移头注释」这种有效指向继续成立。
  ['Vn 迁移版本开发语境', /\bV\d{1,3}\b[^\n]{0,10}(一并落库|落库|阶段|旧版|migration|播种|新建|引入|尚未)/],
  // 机械清理后的破损句：删掉 token 时留下了残缺句法。这里只钉<b>已实际出现过</b>的形态，
  // 不搞宽泛的「中文空格」匹配（正常散文里「与 X 成对」「和 Y 一致」是合法的）。
  // 1) 注释体某行以标点开头 —— 主语被删，后面的话失去了依托（`// ：需求单位必须等于…`）。
  //    `body` 对 `//` 已剥掉 `//`，对块注释保留 `*` 前缀，因此两种形态都要放行。
  ['残句：注释行以标点开头', /^[ \t]*(?:\*+[ \t]*)?[：、，。；][^\n]/m],
  // 2) 虚词/动词被空格孤立后直接接「的」（`不让 的内部码`、`与 的`、`以及 的`）。
  ['残句：空格后接的', /(不让|不把|不给|不再|不是|不会|不能|不需要|以及|和|与|或)\s+的/],
  // 3) 单字虚词两侧全空格（`而 对 只有`）—— 中文里几乎必是断裂。
  ['残句：单字虚词两侧空格', /[\u4e00-\u9fa5]\s+(而|对|为|以|用|从|把|被让)\s+[\u4e00-\u9fa5]/],
  // 4) 旧形态：`： 的`。
  ['机械删除残句', /：\s+的/],
  // 5) 折行残迹：手工断行时行尾落在中文里，拼接后成 `…， 而…`。
  //    只钉<b>无歧义</b>的两类：① 空格后紧跟单字虚词再接汉字（`同 的 处理`）；
  //    ② 空格夹在单字虚词两侧（`而 对 只有`，见规则 3）。
  //    宽泛的「汉字 空格 汉字」与「汉字 空格 否定词」会命中正常排版断行
  //    （`标签页 是否显示`、`不能 全部常驻` 是合法分隔），<b>不上</b>。
  ['残句：行内折行留空格', /[\u4e00-\u9fa5][ \t]+(?:的|而|对|为|以|从|把|被)[ \t]*[\u4e00-\u9fa5]/],
  // 6) markdown 粗体 `**x**`：写作模板的排版语法，会混进注释里当强调。
  //    全范围禁 —— 统一表达成 HTML `<b>x</b>`（JSDoc / Javadoc 都认）。
  //    前后都排除 `/` 以放过 glob（`../views/**/**.vue`）与除法；字符串字面量里的
  //    `'**'`（脱敏掩码）天然不在注释区内，扫描器只看注释体。
  //    <b>必须允许跨行</b>：`**开头…` 换行 `…结尾**` 是手工写 JSDoc 时的常见形态，
  //    单行正则抓不到（实测漏了 6 处）。跨度上限 300 字符防止吞掉整段。
  ['markdown 粗体残迹', /(?<!\/)\*\*(?=[^\s*])(?:(?!\*\*)[\s\S]){0,300}?[^\s*]\*\*(?!\/)/],
];

/**
 * 只在 <b>Java 源码</b> 范围内判定的标记。
 *
 * 前端 `.vue` / `.ts` 里反引号是<b>模板字符串</b>（`` const x = `a${b}` ``），不能禁；
 * Java 的 Javadoc 用 `{@code}`，反引号在这里纯粹是写作模板的残留。
 * （markdown 粗体 `**x**` 则全范围禁，见 `PROCESS_MARKERS` 第 6 条。）
 */
const JAVA_ONLY_MARKERS = [
  ['Javadoc markdown 反引号', /\x60/],
];

/** 声明 Java 范围 key，避免与 `SCOPES` 里的字符串字面量漂移。 */
const JAVA_SCOPE_KEY = 'xsy-scm-server/sa-admin/src/main/java/com/xsy/scm';

const PLAN_MARKERS = [
  ['Sprint / Wave', /\bWave\b/],
  ['Wn 波次', /\bW\d{1,2}\b/],
  ['Rn 阶段代号', /\bR[0-9]\b/],
  ['A-Dn 编号', /\bA-D\d+\b/],
  ['Qna 编号', /\bQ\d{1,2}[a-z]?\b/],
];

/** 判定 `§` 是否指向现存长期文档。 */
const DOC_REFERENCE = /[\w./-]+\.md/;

/**
 * 过程标记<b>必须为 0</b>：任何命中都失败，除非逐条登记在下面的 `WHITELIST` 里。
 *
 * 标记集见 `PROCESS_MARKERS` / `PLAN_MARKERS`。它们覆盖了本仓库历史上出现过的全部
 * 过程形式：来源与复制日期、剪枝适配验收记录、参考项目对比、`Wave` / `Wn` / `Rn` /
 * `A-Dn` / `Qna`、无文档引用的 `§`、`Pn` / `Bn` 批次号、`HD-*`、`A.n`、「新增文件」、
 * 「仿 xxx」、「本阶段」、`Provenance`，以及机械删除留下的残句 `： 的`。
 *
 * 需要保留的例外请登记到 `WHITELIST` 并写明理由，<b>不要放宽标记或调高判定</b>。
 */
/** 显式白名单：确需保留的过程标记逐条登记，`reason` 为空即失败。 */
const WHITELIST = [
  {
    file: 'xsy-scm-web/src/views/business/scm/order/order-return-list.vue',
    marker: 'Bn 批次号（非缺口溯源）',
    reason: '指向 docs/plan/active/frontend-ui-backend-gap-inventory.md 的 B6 缺口溯源，是活契约',
  },
  {
    file: 'xsy-scm-web/src/views/business/scm/promotion/promotion-coupon-list.vue',
    marker: 'Bn 批次号（非缺口溯源）',
    reason: '同上，B7 缺口溯源',
  },
  {
    file: 'xsy-scm-web/src/views/business/scm/inventory/inventory-reservation-list.vue',
    marker: 'Bn 批次号（非缺口溯源）',
    reason: '同上，B8 缺口溯源',
  },
  {
    file: 'xsy-scm-web/src/views/business/scm/finance/finance-detail-drawer.vue',
    marker: 'Bn 批次号（非缺口溯源）',
    reason: '同上，B3 缺口溯源',
  },
  {
    file: 'xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/print/constant/ScmPrintPaperEnum.java',
    marker: 'An 条目号（非业务值）',
    reason: '`{@link #A4}` 是纸张尺寸枚举值，不是计划编号',
  },
  {
    file: 'xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/print/domain/model/ScmPrintTemplateModel.java',
    marker: 'An 条目号（非业务值）',
    reason: '`{@code A4}` 是纸张尺寸，不是计划编号',
  },
  // ---- 测试源码范围（`xsy-scm-web/test`）的例外 ----
  // 测试文件的注释在讲「这条用例为什么存在」，其中的 Pn / Bn 指向<b>现存文档小节</b>，
  // 属活锚点；不是「第几批开发」的过程标记。测试名本身按约定不动。
  {
    file: 'xsy-scm-web/test/p1-sorting-contract.test.mjs',
    marker: 'Pn 批次号（非裁决锚点）',
    reason: 'P1 指向 docs/decisions.md「P1 分拣管理裁决」现存小节，是活锚点',
  },
  {
    file: 'xsy-scm-web/test/p2-delivery-l3-contract.test.mjs',
    marker: 'Pn 批次号（非裁决锚点）',
    reason: 'P2 指向 docs/decisions.md「P2 物流配送 L3 裁决」现存小节，是活锚点',
  },
  {
    file: 'xsy-scm-web/test/w5-delivery-print-contract.test.mjs',
    marker: 'Pn 批次号（非裁决锚点）',
    reason: '`P2 起打印与发车共用同一条幂等通道` 指向 docP2 裁决小节，说明该用例的边界',
  },
  {
    file: 'xsy-scm-web/test/p3-backend-gap-inventory-contract.test.mjs',
    marker: 'Bn 批次号（非缺口溯源）',
    reason: '本文件就是缺口清单（docs/plan/active/frontend-ui-backend-gap-inventory.md）的守卫，'
        + 'B1–B8 是它逐条校验的对象，属活契约',
  },
  {
    file: 'xsy-scm-web/test/ui-shell-contract.test.mjs',
    marker: 'AI 指令式措辞',
    reason: '`V110 不得改动表结构或增删行` 是断言失败消息，描述迁移不得做之事，不是 AI 指令',
  },
  {
    file: 'xsy-scm-web/test/w5-delivery-print-contract.test.mjs',
    marker: 'Provenance 要求',
    reason: '说明「剥注释」的顺序，避免把头部说明当成代码 —— 属实现约束',
  },
  {
    file: 'xsy-scm-web/test/w5-purchase-contract.test.mjs',
    marker: 'Provenance 要求',
    reason: '同上，说明剥离顺序不能反',
  },
];

/**
 * 只需<b>报告</b>的基线（不参与过程标记的成败判定 —— 那一项要求恒为 0）。
 *
 * `anchoredSection`：指向现存文档的 `§` 链接数，<b>只允许不减少</b>（它们是有效引用）。
 * `longCommentCount`：`> 8 行`的注释块数，仅 console.log 输出，<b>不判违规</b>。
 */
const REPORT_BASELINE = {
  'xsy-scm-web/src': {anchoredSection: 4, longCommentCount: 130},
  'xsy-scm-web/test': {anchoredSection: 0, longCommentCount: 0},
  'xsy-scm-server/sa-admin/src/main/java/com/xsy/scm': {anchoredSection: 0, longCommentCount: 396},
  'xsy-scm-server/sa-admin/src/main/resources': {anchoredSection: 0, longCommentCount: 16},
};

// ------------------------------------------------------------------
// 注释抽取（先跳过字符串，再取注释体）
// ------------------------------------------------------------------

const countNewlines = (text, from, to) => {
  let count = 0;
  for (let index = from; index < to; index += 1) {
    if (text.charCodeAt(index) === 10) count += 1;
  }
  return count;
};

/** 正则字面量只能出现在这些字符之后（`/` 是除号时不能当正则起点）。 */
const REGEX_PRECEDERS = new Set([
  '(', ',', '=', ':', '[', '!', '&', '|', '?', '{', '}', ';', '+', '-', '*', '%', '<', '>', '~', '^',
]);

/**
 * 抽取 `//` 与 `/* *\/` 注释，返回 `{start, end, body}`（1 基行号）。
 * 跳过字符串字面量、模板字符串、正则字面量；`allowTemplateLiteral === false` 时按
 * Java 处理（额外跳过 `"""` 文本块，且没有模板字符串）。
 */
function codeComments(text, startLine, allowTemplateLiteral) {
  const found = [];
  const length = text.length;
  let index = 0;
  let line = startLine;
  let previousSignificant = null;

  while (index < length) {
    const character = text[index];

    if (character === '\n') {
      line += 1;
      index += 1;
      continue;
    }
    if (character === ' ' || character === '\t' || character === '\r') {
      index += 1;
      continue;
    }

    // Java 文本块
    if (!allowTemplateLiteral && text.startsWith('"""', index)) {
      let end = text.indexOf('"""', index + 3);
      end = end < 0 ? length : end + 3;
      line += countNewlines(text, index, end);
      index = end;
      previousSignificant = '"';
      continue;
    }

    if (character === '"' || character === "'") {
      index += 1;
      while (index < length) {
        if (text[index] === '\\') {
          index += 2;
          continue;
        }
        if (text[index] === character) {
          index += 1;
          break;
        }
        if (text[index] === '\n') line += 1;
        index += 1;
      }
      previousSignificant = character;
      continue;
    }

    if (allowTemplateLiteral && character === '`') {
      index += 1;
      while (index < length) {
        if (text[index] === '\\') {
          index += 2;
          continue;
        }
        if (text[index] === '`') {
          index += 1;
          break;
        }
        if (text[index] === '\n') line += 1;
        index += 1;
      }
      previousSignificant = '`';
      continue;
    }

    if (text.startsWith('//', index)) {
      let end = text.indexOf('\n', index);
      if (end < 0) end = length;
      found.push({start: line, end: line, body: text.slice(index + 2, end)});
      index = end;
      continue;
    }

    if (text.startsWith('/*', index)) {
      let end = text.indexOf('*/', index + 2);
      end = end < 0 ? length : end + 2;
      const body = text.slice(index, end);
      const lines = countNewlines(text, index, end);
      found.push({start: line, end: line + lines, body});
      line += lines;
      index = end;
      continue;
    }

    // 正则字面量：`/` 不是注释、且上一有效字符允许正则时
    if (character === '/' && (previousSignificant === null || REGEX_PRECEDERS.has(previousSignificant))) {
      index += 1;
      let inClass = false;
      while (index < length) {
        const inner = text[index];
        if (inner === '\\') {
          index += 2;
          continue;
        }
        if (inner === '\n') break;
        if (inner === '[') inClass = true;
        else if (inner === ']') inClass = false;
        else if (inner === '/' && !inClass) {
          index += 1;
          break;
        }
        index += 1;
      }
      while (index < length && /[a-z]/.test(text[index])) index += 1;
      previousSignificant = '/';
      continue;
    }

    previousSignificant = character;
    index += 1;
  }

  return found;
}

function htmlComments(text, startLine) {
  const found = [];
  const pattern = /<!--([\s\S]*?)-->/g;
  let match = pattern.exec(text);
  while (match) {
    found.push({
      start: startLine + countNewlines(text, 0, match.index),
      end: startLine + countNewlines(text, 0, match.index + match[0].length),
      body: match[1],
    });
    match = pattern.exec(text);
  }
  return found;
}

function hashComments(text, startLine) {
  const found = [];
  const pattern = /(^|\n)[ \t]*#[^\n]*/g;
  let match = pattern.exec(text);
  while (match) {
    found.push({
      start: startLine + countNewlines(text, 0, match.index),
      end: startLine + countNewlines(text, 0, match.index + match[0].length),
      body: match[0],
    });
    match = pattern.exec(text);
  }
  return found;
}

/** 顶层 SFC 块切分：`<template>` / `<script>` / `<style>` 约定写在行首。 */
const SFC_BOUNDARY = /^<(template|script|style)\b[^>]*>|^<\/(template|script|style)>/;

function vueComments(text) {
  const found = [];
  const lines = text.split('\n');
  const spans = [];
  let open = null;
  let offset = 0;
  for (const line of lines) {
    const match = SFC_BOUNDARY.exec(line);
    if (match) {
      if (match[1] && !open) open = {name: match[1], from: offset};
      else if (match[2] && open && open.name === match[2]) {
        spans.push({name: open.name, from: open.from, to: offset});
        open = null;
      }
    }
    offset += line.length + 1;
  }
  for (const span of spans) {
    const segment = text.slice(span.from, span.to);
    const startLine = countNewlines(text, 0, span.from) + 1;
    if (span.name === 'template') found.push(...htmlComments(segment, startLine));
    else found.push(...codeComments(segment, startLine, true));
  }
  // SFC 块之外的区域也要扫：文件头常写成 `<template>` 之前的 `<!-- ... -->`
  // （`supplier-sku-drawer.vue` 的来源块就在那里）。漏掉这段会低估命中数。
  let cursor = 0;
  const outside = [];
  for (const span of spans) {
    if (span.from > cursor) outside.push([cursor, span.from]);
    cursor = Math.max(cursor, span.to);
  }
  if (cursor < text.length) outside.push([cursor, text.length]);
  for (const [from, to] of outside) {
    found.push(...htmlComments(text.slice(from, to), countNewlines(text, 0, from) + 1));
  }
  return found;
}

function commentsOf(path) {
  const text = readFileSync(path, 'utf8');
  const extension = path.slice(path.lastIndexOf('.')).toLowerCase();
  if (extension === '.vue') return vueComments(text);
  if (extension === '.java') return codeComments(text, 1, false);
  if (['.ts', '.js', '.mjs', '.tsx', '.less'].includes(extension)) return codeComments(text, 1, true);
  if (extension === '.xml') return htmlComments(text, 1);
  if (['.yml', '.yaml', '.properties'].includes(extension)) return hashComments(text, 1);
  return [];
}

// ------------------------------------------------------------------
// 扫描
// ------------------------------------------------------------------

function collectFiles(root, extensions, excludedFiles = new Set()) {
  const files = [];
  const walk = (directory) => {
    let entries;
    try {
      entries = readdirSync(directory, {withFileTypes: true});
    } catch {
      return;
    }
    for (const entry of entries) {
      const path = `${directory}/${entry.name}`;
      if (entry.isDirectory()) {
        if (EXCLUDED_DIRECTORIES.has(entry.name)) continue;
        if (path.includes(FLYWAY_DIRECTORY)) continue;
        walk(path);
      } else if (extensions.has(path.slice(path.lastIndexOf('.')).toLowerCase())) {
        if (excludedFiles.has(path.slice(REPO.length))) continue;
        files.push(path);
      }
    }
  };
  walk(root);
  return files.sort();
}

/** 扫描一个范围，返回各标记命中数、`§` 分类与长注释计数。 */
function scanScope(scope) {
  const hits = new Map();
  let anchoredSection = 0;
  let longCommentCount = 0;

  const record = (key, relative) => {
    if (!hits.has(key)) hits.set(key, []);
    hits.get(key).push(relative);
  };

  const files = collectFiles(scope.root, scope.extensions, scope.excludedFiles ?? new Set());
  const markers = scope.key === JAVA_SCOPE_KEY
    ? [...PROCESS_MARKERS, ...PLAN_MARKERS, ...JAVA_ONLY_MARKERS]
    : [...PROCESS_MARKERS, ...PLAN_MARKERS];
  for (const file of files) {
    const relative = file.slice(REPO.length);
    for (const {start, end, body} of commentsOf(file)) {
      if (end - start + 1 > 8) longCommentCount += 1;
      for (const [key, pattern] of markers) {
        if (pattern.test(body)) record(key, relative);
      }
      const hasDocumentReference = DOC_REFERENCE.test(body);
      const sections = body.match(/§/g);
      if (sections) {
        if (hasDocumentReference) anchoredSection += sections.length;
        else for (const _ of sections) record('§ 无 .md 引用', relative);
      }
    }
  }

  return {files: files.length, hits, anchoredSection, longCommentCount};
}

const RESULTS = new Map(SCOPES.map((scope) => [scope.key, scanScope(scope)]));

// ------------------------------------------------------------------
// 断言
// ------------------------------------------------------------------

test('扫描基线有效：四个范围都能被枚举并解析', () => {
  for (const scope of SCOPES) {
    const result = RESULTS.get(scope.key);
    // 测试目录是单层 `.mjs`，不适用「> 50 个文件」的体量门槛；其余范围沿用。
    const minimum = scope.key === 'xsy-scm-web/test' ? 30 : 50;
    assert.ok(result.files > minimum,
        `${scope.key} 只枚举到 ${result.files} 个文件，扫描方式可能已失效`);
  }
  // 反向护栏：如果抽取器坏了（比如把整个文件当成注释），长注释数会异常膨胀
  const web = RESULTS.get('xsy-scm-web/src');
  assert.ok(web.longCommentCount < 400,
      `前端长注释块 ${web.longCommentCount} 个，疑似注释抽取器失效`);
});

test('Flyway 已应用 migration 不在扫描范围内', () => {
  const resources = SCOPES.find((scope) => scope.key.endsWith('resources'));
  const files = collectFiles(resources.root, resources.extensions);
  const migrations = files.filter((file) => file.includes(FLYWAY_DIRECTORY));
  assert.deepEqual(migrations, [], 'db/migration 被纳入了扫描；已应用的 migration 不得修改');
  assert.ok(files.length > 10, 'resources 范围扫描不到文件，排除规则可能写错了');
  // 直接确认排除规则本身生效（而不是靠扩展名恰好不匹配兜住）
  assert.ok(readdirSync(`${REPO}xsy-scm-server/sa-admin/src/main/resources/db/migration`).length > 50,
      'migration 目录不存在或为空，排除规则没有被真正验证');
});

test('过程标记必须为 0（未登记白名单的一律失败）', () => {
  assert.ok(
      WHITELIST.every((entry) => entry.reason && entry.reason.trim()),
      'WHITELIST 的每条都要写 reason，否则等于无理由放宽',
  );
  const allowed = new Set(WHITELIST.map((entry) => `${entry.marker}\u0000${entry.file}`));
  const failures = [];
  for (const scope of SCOPES) {
    const {hits} = RESULTS.get(scope.key);
    // 断言列表必须与 `scanScope` 实际使用的标记集一致，否则新规则记录后无人校验。
    const checked = scope.key === JAVA_SCOPE_KEY
      ? [...PROCESS_MARKERS, ...PLAN_MARKERS, ...JAVA_ONLY_MARKERS, ['§ 无 .md 引用']]
      : [...PROCESS_MARKERS, ...PLAN_MARKERS, ['§ 无 .md 引用']];
    for (const [key] of checked) {
      const remaining = (hits.get(key) ?? []).filter((file) => !allowed.has(`${key}\u0000${file}`));
      if (remaining.length) {
        failures.push(`${scope.key} 的「${key}」${remaining.length} 处：${[...new Set(remaining)].slice(0, 4).join(', ')}`);
      }
    }
  }
  assert.deepEqual(failures, [],
      '新增了 AI 开发过程残留注释。注释只解释当前代码中无法直接看出的约束，不记录代码是怎么被开发出来的；' +
      '确需保留请登记到 WHITELIST 并写明理由（见 docs/architecture/code-comment-guidelines.md）');
});

test('指向现存文档的 § 链接不得被误删', () => {
  for (const scope of SCOPES) {
    const {anchoredSection} = RESULTS.get(scope.key);
    const expected = REPORT_BASELINE[scope.key].anchoredSection;
    assert.ok(anchoredSection >= expected,
        `${scope.key} 指向现存文档的 § 链接从 ${expected} 降到 ${anchoredSection}；` +
        '`长期规则见 docs/architecture/xxx.md §5` 是有效引用，不是过程标记');
  }
});

test('长注释计数：仅报告，不判违规', () => {
  // 故意不断言上限：长度是审计信号，不是质量判定。
  // 否则开发者为了过测试会把 9 行有价值的说明硬压成 5 行，反而降低代码质量。
  const report = SCOPES.map((scope) => {
    const {longCommentCount, anchoredSection} = RESULTS.get(scope.key);
    const short = scope.key.replace('xsy-scm-server/sa-admin/src/', '');
    return `${short} >8行=${longCommentCount} 合法§链接=${anchoredSection}`;
  });
  console.log(`[scm-comment-noise] 审计信号（非违规）：${report.join(' | ')}`);
  assert.ok(true);
});
