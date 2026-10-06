/**
 * SCM 生产源码注释噪声契约。
 *
 * 只约束**开发过程标记**（来源 / 复制日期 / 波次 / 阶段号 / 计划章节号），不限制注释长度
 * —— 长度是审计信号，`longCommentCount` 只输出报告。
 *
 * 标记用组合 / 条件匹配，避免误报：裸 `来源：` 会命中业务含义（`来源：销售订单`），
 * 裸 `§` 会命中指向 `docs/architecture` 与 `docs/adr` 的有效链接。
 *
 * 分两阶段：现在 `count <= BASELINE` 且逐标记比较，P4.4 完成后再收紧到 0。
 * 详细规则、基线与扫描口径见 `docs/architecture/code-comment-guidelines.md`。
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

/** Flyway 迁移目录：只从扫描中排除，永远不修改。目录名本身不带尾斜杠，故不加尾斜杠比对。 */
const FLYWAY_DIRECTORY = '/db/migration';

// ------------------------------------------------------------------
// 标记集（组合 / 条件匹配，见文件头）
// ------------------------------------------------------------------

const PROCESS_MARKERS = [
  ['来源：project-reference-examples', /来源\s*[:：]\s*\**\s*project-reference-examples/],
  ['来源：新写', /来源\s*[:：]\s*\**\s*新写/],
  // `来源：**W1 派生**` 是第三种形态，P4.1 的组合匹配没覆盖到
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
];

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
 * 基线（**P4.6b 已完成：三个范围的标记基线全部为 0**）。
 *
 * **逐标记比较**：任一标记新增即失败，即使总数因别的标记下降而没超基线。
 *
 * P4.1 / P4.2 清空了前端的来源块与计划编号，P4.5 清空了后端的
 * Wave / Wn / Rn / A-Dn / Qna / § 章节号，因此现在 `count <= 0` 就是
 * 「发现新增就失败」—— 这是最终要的状态。需要保留的例外请显式登记在
 * {@link WHITELIST} 并写明理由，**不要调高这里的数字**。
 */
/** 显式白名单：确需保留的过程标记写在这里，{@code reason} 为空即失败。当前为空。 */
const WHITELIST = [
  // {file: 'path/to/File.java', marker: 'Wn 波次', reason: '说明为什么这条必须留'},
];

const BASELINE = {
  'xsy-scm-web/src': {
    process: {
      // P4.1 清空 54 个文件头的来源块；P4.2 顺手清掉 4 处 `来源：**W1 派生**`
      '来源：project-reference-examples': 0,
      '来源：新写': 0,
      '来源：Wn 派生': 0,
      '复制日期': 0,
      'Copy First + Adapt': 0,
      '剪枝 / 适配 / 验收记录': 0,
      '测试 / 验收记录': 0,
      '参考项目 / 旧项目对比': 0,
      '为什么新增此文件': 0,
      'AI 指令式措辞': 0,
    },
    plan: {
      // P4.2 清空：Wave / Wn / Rn / A-Dn / Qna / 无 .md 引用的 §
      'Sprint / Wave': 0,
      'Wn 波次': 0,
      'Rn 阶段代号': 0,
      'A-Dn 编号': 0,
      'Qna 编号': 0,
      '§ 无 .md 引用': 0,
    },
    // 指向现存文档的 § 是有效链接，只允许不减少
    anchoredSection: 4,
    longCommentCount: 157,
  },
  'xsy-scm-server/sa-admin/src/main/java/com/xsy/scm': {
    process: {
      '来源：project-reference-examples': 0,
      '来源：新写': 0,
      '来源：Wn 派生': 0,
      '复制日期': 0,
      'Copy First + Adapt': 0,
      '剪枝 / 适配 / 验收记录': 0,
      '测试 / 验收记录': 0,
      '参考项目 / 旧项目对比': 0,
      '为什么新增此文件': 0,
      'AI 指令式措辞': 0,
    },
    plan: {
      'Sprint / Wave': 0,
      'Wn 波次': 0,
      'Rn 阶段代号': 0,
      'A-Dn 编号': 0,
      'Qna 编号': 0,
      '§ 无 .md 引用': 0,
    },
    anchoredSection: 0,
    longCommentCount: 401,
  },
  'xsy-scm-server/sa-admin/src/main/resources': {
    process: {
      '来源：project-reference-examples': 0,
      '来源：新写': 0,
      '来源：Wn 派生': 0,
      '复制日期': 0,
      'Copy First + Adapt': 0,
      '剪枝 / 适配 / 验收记录': 0,
      '测试 / 验收记录': 0,
      '参考项目 / 旧项目对比': 0,
      '为什么新增此文件': 0,
      'AI 指令式措辞': 0,
    },
    plan: {
      'Sprint / Wave': 0,
      'Wn 波次': 0,
      'Rn 阶段代号': 0,
      'A-Dn 编号': 0,
      'Qna 编号': 0,
      '§ 无 .md 引用': 0,
    },
    // resources 里那 4 处「§」其实是 `设计稿 §0` 这类计划章节号，不是文档链接，已随 P4.5 清除；
    // 该指标是启发式护栏（块内含 .md 就认为 § 合法），在 resources 上会误判，故基线为 0。
    anchoredSection: 0,
    longCommentCount: 16,
  },
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

function collectFiles(root, extensions) {
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
        files.push(path);
      }
    }
  };
  walk(root);
  return files.sort();
}

/** 扫描一个范围，返回各标记命中数、`§` 分类与长注释计数。 */
function scanScope(scope) {
  const processCounts = Object.fromEntries(PROCESS_MARKERS.map(([key]) => [key, 0]));
  const planCounts = Object.fromEntries(PLAN_MARKERS.map(([key]) => [key, 0]));
  planCounts['§ 无 .md 引用'] = 0;
  let anchoredSection = 0;
  let longCommentCount = 0;
  const offenders = new Map();

  const files = collectFiles(scope.root, scope.extensions);
  for (const file of files) {
    const relative = file.slice(REPO.length);
    for (const {start, end, body} of commentsOf(file)) {
      if (end - start + 1 > 8) longCommentCount += 1;
      for (const [key, pattern] of [...PROCESS_MARKERS, ...PLAN_MARKERS]) {
        if (!pattern.test(body)) continue;
        const bucket = PROCESS_MARKERS.some(([name]) => name === key) ? processCounts : planCounts;
        bucket[key] += 1;
        if (!offenders.has(key)) offenders.set(key, []);
        const list = offenders.get(key);
        if (list.length < 4) list.push(`${relative}:${start}`);
      }
      const hasDocumentReference = DOC_REFERENCE.test(body);
      const sections = body.match(/§/g);
      if (sections) {
        if (hasDocumentReference) anchoredSection += sections.length;
        else {
          planCounts['§ 无 .md 引用'] += sections.length;
          if (!offenders.has('§ 无 .md 引用')) offenders.set('§ 无 .md 引用', []);
          const list = offenders.get('§ 无 .md 引用');
          if (list.length < 4) list.push(`${relative}:${start}`);
        }
      }
    }
  }

  return {files: files.length, processCounts, planCounts, anchoredSection, longCommentCount, offenders};
}

const RESULTS = new Map(SCOPES.map((scope) => [scope.key, scanScope(scope)]));

// ------------------------------------------------------------------
// 断言
// ------------------------------------------------------------------

test('扫描基线有效：三个范围都能被枚举并解析', () => {
  for (const scope of SCOPES) {
    const result = RESULTS.get(scope.key);
    assert.ok(result.files > 50, `${scope.key} 只枚举到 ${result.files} 个文件，扫描方式可能已失效`);
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

test('过程标记必须为 0（P4.6b：逐标记棘轮已收紧）', () => {
  const failures = [];
  for (const scope of SCOPES) {
    const {processCounts, offenders} = RESULTS.get(scope.key);
    const baseline = BASELINE[scope.key].process;
    for (const [key, expected] of Object.entries(baseline)) {
      const actual = processCounts[key];
      if (actual > expected) {
        failures.push(`${scope.key} 的「${key}」从 ${expected} 涨到 ${actual}` +
            `（${(offenders.get(key) ?? []).join(', ')}）`);
      }
    }
  }
  assert.deepEqual(failures, [],
      '新增了 AI 开发过程残留注释。注释只解释当前代码中无法直接看出的约束，' +
      '不记录代码是怎么被开发出来的；确需保留请走 BASELINE 白名单并说明理由');
});

test('计划编号必须为 0（P4.6b：逐标记棘轮已收紧）', () => {
  const failures = [];
  for (const scope of SCOPES) {
    const {planCounts, offenders} = RESULTS.get(scope.key);
    const baseline = BASELINE[scope.key].plan;
    for (const [key, expected] of Object.entries(baseline)) {
      const actual = planCounts[key];
      if (actual > expected) {
        failures.push(`${scope.key} 的「${key}」从 ${expected} 涨到 ${actual}` +
            `（${(offenders.get(key) ?? []).join(', ')}）`);
      }
    }
  }
  assert.deepEqual(failures, [],
      '新增了对已失效计划文档的引用（Wave / Wn / Rn / A-Dn / Qna / §章节号）。' +
      '这些编号读者无从追溯，请改为不依赖编号的陈述');
});

test('指向现存文档的 § 链接不得被误删', () => {
  for (const scope of SCOPES) {
    const {anchoredSection} = RESULTS.get(scope.key);
    const expected = BASELINE[scope.key].anchoredSection;
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
